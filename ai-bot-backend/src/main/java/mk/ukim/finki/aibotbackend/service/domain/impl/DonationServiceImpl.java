package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.integration.vezilka.BatchVezilkaClient;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationItemResult;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationResponse;
import mk.ukim.finki.aibotbackend.integration.vezilka.TextDonationItem;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class DonationServiceImpl implements DonationService {
    /** Reported to Vezilka as page_language. */
    private static final String MACEDONIAN_LANGUAGE_TAG = "mk";
    /** Automatic retries stop after this many submissions. A person can still retry a FAILED batch. */
    public static final int MAX_SUBMISSION_ATTEMPTS = 5;
    /** While a submission is in flight the batch is not due, so the scheduler and a second request skip it. */
    private static final Duration SUBMISSION_LEASE = Duration.ofMinutes(15);
    private static final int MAX_ERROR_LENGTH = 1000;

    /** A leased batch and the posts it still has to send, in a fixed order. */
    private record Claim(Long batchId, List<Long> pendingPostIds) {
    }

    private record Failure(DonationBatch batch, boolean anyProcessed) {
    }

    private final DonationBatchRepository donationBatchRepository;
    private final ExtractedPostService extractedPostService;
    private final BatchVezilkaClient vezilkaClient;
    private final TransactionTemplate transaction;

    public DonationServiceImpl(DonationBatchRepository donationBatchRepository,
                               ExtractedPostService extractedPostService, BatchVezilkaClient vezilkaClient,
                               PlatformTransactionManager transactionManager) {
        this.donationBatchRepository = donationBatchRepository;
        this.extractedPostService = extractedPostService;
        this.vezilkaClient = vezilkaClient;
        this.transaction = new TransactionTemplate(transactionManager);
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
        if (postIds == null || postIds.isEmpty() || postIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidDonationStateException("Select at least one post.");
        }
        List<ExtractedPost> posts = extractedPostService.findAllById(postIds);
        Map<Long, ExtractedPost> byId = index(posts);
        for (Long postId : postIds) {
            ExtractedPost post = byId.get(postId);
            if (post == null) {
                throw new PostNotFoundException(postId);
            }
            if (post.getDonationBatch() != null) {
                throw new InvalidDonationStateException("Post %d already belongs to batch %d."
                    .formatted(postId, post.getDonationBatch().getId()));
            }
            if (post.getContent() == null || post.getContent().strip().length() < 20
                || post.getContent().length() > 100_000 || post.getSourceUrl() == null
                || post.getSourceUrl().isBlank()) {
                throw new InvalidDonationStateException("Post %d needs a source URL and 20 to 100,000 characters of text."
                    .formatted(postId));
            }
            if (post.getMacedonianConfidence() == null || post.getMacedonianConfidence() < 0.6) {
                throw new InvalidDonationStateException("Post %d has insufficient Macedonian language evidence."
                    .formatted(postId));
            }
        }
        DonationBatch batch = donationBatchRepository.save(new DonationBatch(DonationStatus.DRAFT));
        posts.forEach(post -> post.setDonationBatch(batch));
        extractedPostService.saveAll(posts);
        batch.getPosts().addAll(posts);
        return batch;
    }

    @Override
    @Transactional
    public DonationBatch approve(Long id) {
        DonationBatch batch = getForUpdate(id);
        if (batch.getStatus() != DonationStatus.DRAFT || batch.getPosts().isEmpty()) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        batch.setStatus(DonationStatus.APPROVED);
        return donationBatchRepository.save(batch);
    }

    @Override
    public DonationBatch submit(Long id) {
        Claim claim = transaction.execute(status -> claim(id, true));
        return runSubmission(claim);
    }

    @Override
    public void refreshSubmittedStatuses() {
        for (Long id : donationBatchRepository.findDueIdsByStatus(DonationStatus.SUBMITTED, Instant.now())) {
            try {
                Claim claim = transaction.execute(status -> claim(id, false));
                if (claim != null) {
                    runSubmission(claim);
                }
            } catch (RuntimeException exception) {
                // One broken batch must not stop the retries of the others.
                log.warn("Donation batch {} retry failed: {}", id, exception.getMessage());
            }
        }
    }

    /**
     * Locks the batch, checks that it may be sent now, and leases it for one
     * submission. The lock ends with this transaction, before any HTTP call.
     * Returns null when a scheduled retry finds the batch no longer due.
     */
    private Claim claim(Long id, boolean manual) {
        DonationBatch batch = getForUpdate(id);
        boolean due = batch.getNextRetryAt() == null || !Instant.now().isBefore(batch.getNextRetryAt());
        if (manual) {
            boolean submittable = batch.getStatus() == DonationStatus.APPROVED
                || batch.getStatus() == DonationStatus.SUBMITTED || batch.getStatus() == DonationStatus.FAILED;
            if (!submittable) {
                throw new InvalidDonationStateException(id, batch.getStatus());
            }
            if (!due) {
                throw new InvalidDonationStateException("Retry this batch after " + batch.getNextRetryAt());
            }
        } else if (batch.getStatus() != DonationStatus.SUBMITTED || !due) {
            return null;
        }
        batch.setStatus(DonationStatus.SUBMITTED);
        batch.setNextRetryAt(Instant.now().plus(SUBMISSION_LEASE));
        batch.setAttemptCount(batch.getAttemptCount() + 1);
        donationBatchRepository.save(batch);
        // The POST verdict is final. Only posts without one are sent.
        return new Claim(id, batch.getPosts().stream()
            .filter(post -> post.getDonationStatus() == null)
            .map(ExtractedPost::getId)
            .toList());
    }

    /** Sends the leased posts chunk by chunk. Each chunk's verdicts commit before the next request. */
    private DonationBatch runSubmission(Claim claim) {
        List<Long> pending = claim.pendingPostIds();
        for (int from = 0; from < pending.size(); from += BatchVezilkaClient.MAX_ITEMS_PER_REQUEST) {
            List<Long> chunk = pending.subList(from,
                Math.min(from + BatchVezilkaClient.MAX_ITEMS_PER_REQUEST, pending.size()));
            List<TextDonationItem> items = transaction.execute(status -> toItems(chunk));
            DonationResponse response;
            try {
                response = vezilkaClient.submitTextDonations(items);
            } catch (VezilkaIntegrationException exception) {
                Failure failure = transaction.execute(status -> recordFailure(claim.batchId(), exception));
                if (!failure.anyProcessed()) {
                    // Nothing reached the corpus, so the caller learns why the whole submission failed.
                    throw exception;
                }
                return failure.batch();
            }
            transaction.executeWithoutResult(status -> recordVerdicts(chunk, response));
        }
        return transaction.execute(status -> settle(claim.batchId()));
    }

    private List<TextDonationItem> toItems(List<Long> postIds) {
        Map<Long, ExtractedPost> byId = index(extractedPostService.findAllById(postIds));
        return postIds.stream().map(byId::get)
            .map(post -> new TextDonationItem(post.getSourceUrl(), post.getContent(), null,
                MACEDONIAN_LANGUAGE_TAG, retrievedAt(post)))
            .toList();
    }

    /** The extraction time, as the ISO-8601 instant the API documents for {@code retrieved_at}. */
    private static String retrievedAt(ExtractedPost post) {
        return post.getCreatedAt() == null ? null
            : post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    private void recordVerdicts(List<Long> postIds, DonationResponse response) {
        // The client has already checked the response: one well-formed result per item, in request order.
        Map<Long, ExtractedPost> byId = index(extractedPostService.findAllById(postIds));
        List<ExtractedPost> posts = postIds.stream().map(byId::get).toList();
        for (int index = 0; index < posts.size(); index++) {
            DonationItemResult result = response.results().get(index);
            ExtractedPost post = posts.get(index);
            post.setVezilkaId(result.id());
            post.setDonationStatus(result.isAccepted() ? DonationStatus.ACCEPTED : DonationStatus.REJECTED);
            post.setRejectionReason(result.isAccepted() ? null : result.describeRejection());
        }
        extractedPostService.saveAll(posts);
    }

    private Failure recordFailure(Long batchId, VezilkaIntegrationException exception) {
        DonationBatch batch = getForUpdate(batchId);
        boolean anyProcessed = batch.getPosts().stream().anyMatch(post -> post.getDonationStatus() != null);
        boolean giveUp = !exception.isRetryable() || batch.getAttemptCount() >= MAX_SUBMISSION_ATTEMPTS;
        if (giveUp) {
            batch.setStatus(DonationStatus.FAILED);
            batch.setNextRetryAt(null);
            log.warn("Donation batch {} failed for good after {} attempts: {}",
                batchId, batch.getAttemptCount(), exception.getMessage());
        } else {
            batch.setStatus(anyProcessed ? DonationStatus.SUBMITTED : DonationStatus.APPROVED);
            batch.setNextRetryAt(exception.getRetryAt());
            log.warn("Donation batch {} has unsent posts; retry after {}: {}",
                batchId, batch.getNextRetryAt(), exception.getMessage());
        }
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        batch.setLastError(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        if (anyProcessed) {
            batch.setSubmittedAt(LocalDateTime.now());
        }
        updateReference(batch);
        return new Failure(donationBatchRepository.save(batch), anyProcessed);
    }

    private DonationBatch settle(Long batchId) {
        DonationBatch batch = getForUpdate(batchId);
        batch.setNextRetryAt(null);
        batch.setLastError(null);
        batch.setSubmittedAt(LocalDateTime.now());
        updateReference(batch);
        boolean pending = batch.getPosts().stream().anyMatch(post -> post.getDonationStatus() == null);
        boolean accepted = batch.getPosts().stream().anyMatch(post -> post.getDonationStatus() == DonationStatus.ACCEPTED);
        batch.setStatus(pending ? DonationStatus.SUBMITTED : accepted ? DonationStatus.ACCEPTED : DonationStatus.REJECTED);
        return donationBatchRepository.save(batch);
    }

    private void updateReference(DonationBatch batch) {
        batch.setVezilkaReference(batch.getPosts().stream()
            .filter(post -> post.getDonationStatus() == DonationStatus.ACCEPTED)
            .map(ExtractedPost::getVezilkaId)
            .filter(Objects::nonNull) // deduped posts are accepted without an id
            .findFirst().orElse(null));
    }

    private static Map<Long, ExtractedPost> index(List<ExtractedPost> posts) {
        return posts.stream().collect(Collectors.toMap(ExtractedPost::getId, Function.identity()));
    }

    private DonationBatch getForUpdate(Long id) {
        return donationBatchRepository.findForUpdate(id)
            .orElseThrow(() -> new DonationBatchNotFoundException(id));
    }
}
