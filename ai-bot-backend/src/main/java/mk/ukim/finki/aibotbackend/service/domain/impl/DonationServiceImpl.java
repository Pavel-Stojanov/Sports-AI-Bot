package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationItemResult;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationResponse;
import mk.ukim.finki.aibotbackend.integration.vezilka.TextDonationItem;
import mk.ukim.finki.aibotbackend.integration.vezilka.VezilkaClient;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.DonationBatchNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.repository.DonationBatchRepository;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class DonationServiceImpl implements DonationService {
    /** Reported to Vezilka as {@code page_language}; informative on their side. */
    private static final String MACEDONIAN_LANGUAGE_TAG = "mk";

    private final DonationBatchRepository donationBatchRepository;
    private final ExtractedPostService extractedPostService;
    private final VezilkaClient vezilkaClient;

    public DonationServiceImpl(
        DonationBatchRepository donationBatchRepository,
        ExtractedPostService extractedPostService,
        VezilkaClient vezilkaClient
    ) {
        this.donationBatchRepository = donationBatchRepository;
        this.extractedPostService = extractedPostService;
        this.vezilkaClient = vezilkaClient;
    }

    @Override
    public List<DonationBatch> findAll() {
        return donationBatchRepository.findAll();
    }

    @Override
    public Optional<DonationBatch> findById(Long id) {
        return donationBatchRepository.findById(id);
    }

    @Override
    @Transactional
    public DonationBatch createBatch(List<Long> postIds) {
        List<ExtractedPost> posts = extractedPostService.findAllById(postIds);
        for (Long postId : postIds) {
            if (posts.stream().noneMatch(post -> post.getId().equals(postId))) {
                throw new PostNotFoundException(postId);
            }
        }
        DonationBatch batch = donationBatchRepository.save(new DonationBatch(DonationStatus.DRAFT));
        posts.forEach(post -> post.setDonationBatch(batch));
        extractedPostService.saveAll(posts);
        batch.getPosts().addAll(posts);
        return batch;
    }

    @Override
    public DonationBatch approve(Long id) {
        DonationBatch batch = getOrThrow(id);
        if (batch.getStatus() != DonationStatus.DRAFT) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        batch.setStatus(DonationStatus.APPROVED);
        return donationBatchRepository.save(batch);
    }

    @Override
    @Transactional
    public DonationBatch submit(Long id) {
        DonationBatch batch = getOrThrow(id);
        if (batch.getStatus() != DonationStatus.APPROVED) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        List<ExtractedPost> posts = batch.getPosts();
        int accepted = 0;
        int donated = 0;
        // Vezilka answers per item, so the posts of one batch travel as items of
        // the same request — in chunks, since a request carries at most 100.
        for (int from = 0; from < posts.size(); from += VezilkaClient.MAX_ITEMS_PER_REQUEST) {
            List<ExtractedPost> chunk =
                posts.subList(from, Math.min(from + VezilkaClient.MAX_ITEMS_PER_REQUEST, posts.size()));
            try {
                DonationResponse response = vezilkaClient.submitTextDonations(
                    chunk.stream().map(this::toDonationItem).toList());
                accepted += applyResults(chunk, response);
                donated += chunk.size();
            } catch (VezilkaIntegrationException exception) {
                if (donated == 0) {
                    // Nothing reached the corpus — fail the whole submission and
                    // leave the batch APPROVED so it can be submitted again.
                    throw exception;
                }
                // A later chunk failed, typically on the hourly rate limit. The
                // earlier verdicts are real and must not be thrown away.
                log.warn("Donation batch {} stopped after {} of {} posts: {}",
                    batch.getId(), donated, posts.size(), exception.getMessage());
                break;
            }
        }
        extractedPostService.saveAll(posts);
        batch.setVezilkaReference(referenceOf(posts));
        batch.setSubmittedAt(LocalDateTime.now());
        batch.setStatus(settle(accepted, donated, posts.size()));
        log.info("Donation batch {}: {} of {} donated posts accepted by Vezilka.",
            batch.getId(), accepted, donated);
        return donationBatchRepository.save(batch);
    }

    @Override
    @Transactional
    public void refreshSubmittedStatuses() {
        for (DonationBatch batch : donationBatchRepository.findAllByStatus(DonationStatus.SUBMITTED)) {
            try {
                // Moderation was already decided when the posts were donated, so
                // this confirms the recorded verdicts rather than waiting for one.
                int accepted = 0;
                int donated = 0;
                for (ExtractedPost post : batch.getPosts()) {
                    if (post.getVezilkaId() == null) {
                        continue;
                    }
                    donated++;
                    if (vezilkaClient.checkStatus(post.getVezilkaId()) == DonationStatus.ACCEPTED) {
                        accepted++;
                        post.setRejectionReason(null);
                    }
                }
                if (donated == 0) {
                    log.warn("Donation batch {} is SUBMITTED but no post reached Vezilka; "
                        + "its posts need a new batch.", batch.getId());
                    continue;
                }
                extractedPostService.saveAll(batch.getPosts());
                batch.setStatus(accepted > 0 ? DonationStatus.ACCEPTED : DonationStatus.REJECTED);
                donationBatchRepository.save(batch);
                log.info("Donation batch {} settled as {}: {} of {} donated posts accepted.",
                    batch.getId(), batch.getStatus(), accepted, donated);
            } catch (VezilkaIntegrationException exception) {
                log.warn("Could not refresh status of donation batch {}: {}",
                    batch.getId(), exception.getMessage());
            }
        }
    }

    /**
     * A batch that was donated in full is decided; one cut short keeps its
     * verdicts but stays SUBMITTED for {@link #refreshSubmittedStatuses()}.
     */
    private DonationStatus settle(int accepted, int donated, int total) {
        if (donated < total) {
            return DonationStatus.SUBMITTED;
        }
        return accepted > 0 ? DonationStatus.ACCEPTED : DonationStatus.REJECTED;
    }

    /**
     * Writes each verdict onto the post that produced it. Results come back in
     * the order the items were sent, which is the order of {@code chunk}.
     */
    private int applyResults(List<ExtractedPost> chunk, DonationResponse response) {
        List<DonationItemResult> results = response.results();
        if (results == null || results.size() != chunk.size()) {
            throw new VezilkaIntegrationException(
                "Vezilka returned %s results for %d donated posts."
                    .formatted(results == null ? "no" : results.size(), chunk.size()));
        }
        int accepted = 0;
        for (int index = 0; index < chunk.size(); index++) {
            DonationItemResult result = results.get(index);
            ExtractedPost post = chunk.get(index);
            post.setVezilkaId(result.id());
            post.setRejectionReason(result.isAccepted() ? null : result.rejectionReason());
            if (result.isAccepted()) {
                accepted++;
            }
        }
        return accepted;
    }

    private TextDonationItem toDonationItem(ExtractedPost post) {
        String text = post.getSummary() != null && !post.getSummary().isBlank()
            ? post.getSummary()
            : post.getContent();
        return new TextDonationItem(
            post.getSourceUrl(),
            text,
            null,
            MACEDONIAN_LANGUAGE_TAG,
            post.getPostedAt() == null
                ? null
                : post.getPostedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
        );
    }

    /**
     * A convenience handle for reading the batch back: the Vezilka id of its
     * first accepted post. The individual ids live on the posts themselves.
     */
    private String referenceOf(List<ExtractedPost> posts) {
        return posts.stream()
            .filter(post -> post.getRejectionReason() == null && post.getVezilkaId() != null)
            .map(ExtractedPost::getVezilkaId)
            .findFirst()
            .orElse(null);
    }

    private DonationBatch getOrThrow(Long id) {
        return donationBatchRepository
            .findById(id)
            .orElseThrow(() -> new DonationBatchNotFoundException(id));
    }
}
