package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.util.Objects;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final DonationBatchRepository donationBatchRepository;
    private final ExtractedPostService extractedPostService;
    private final BatchVezilkaClient vezilkaClient;
    private final TransactionTemplate retryTransaction;

    public DonationServiceImpl(DonationBatchRepository donationBatchRepository,
                               ExtractedPostService extractedPostService, BatchVezilkaClient vezilkaClient,
                               PlatformTransactionManager transactionManager) {
        this.donationBatchRepository = donationBatchRepository;
        this.extractedPostService = extractedPostService;
        this.vezilkaClient = vezilkaClient;
        this.retryTransaction = new TransactionTemplate(transactionManager);
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
        for (Long postId : postIds) {
            ExtractedPost post = posts.stream().filter(item -> item.getId().equals(postId))
                .findFirst().orElseThrow(() -> new PostNotFoundException(postId));
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
    @Transactional(noRollbackFor = VezilkaIntegrationException.class)
    public DonationBatch submit(Long id) {
        DonationBatch batch = getForUpdate(id);
        if (batch.getStatus() != DonationStatus.APPROVED && batch.getStatus() != DonationStatus.SUBMITTED) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        if (batch.getNextRetryAt() != null && Instant.now().isBefore(batch.getNextRetryAt())) {
            throw new InvalidDonationStateException("Retry this batch after " + batch.getNextRetryAt());
        }
        return submitPending(batch);
    }

    @Override
    public void refreshSubmittedStatuses() {
        for (Long candidateId : donationBatchRepository.findIdsByStatus(DonationStatus.SUBMITTED)) {
            try {
                // Each batch commits on its own. Verdicts already stored for one batch
                // must survive a failure while storing another batch's verdicts.
                retryTransaction.executeWithoutResult(status -> retryBatch(candidateId));
            } catch (RuntimeException exception) {
                // One broken batch must not stop the retries of the others.
                log.warn("Donation batch {} retry failed: {}", candidateId, exception.getMessage());
            }
        }
    }

    private void retryBatch(Long id) {
        DonationBatch batch = getForUpdate(id);
        if (batch.getStatus() != DonationStatus.SUBMITTED
            || (batch.getNextRetryAt() != null && Instant.now().isBefore(batch.getNextRetryAt()))) {
            return;
        }
        try {
            // The POST verdict is final. Retry missing items, never rejected or accepted items.
            submitPending(batch);
        } catch (VezilkaIntegrationException exception) {
            // The retry delay is already stored on the batch and must be committed.
            log.warn("Donation batch {} retry failed: {}", id, exception.getMessage());
        }
    }

    private DonationBatch submitPending(DonationBatch batch) {
        List<ExtractedPost> pending = batch.getPosts().stream()
            .filter(post -> post.getDonationStatus() == null).toList();
        batch.setNextRetryAt(null);
        for (int from = 0; from < pending.size(); from += BatchVezilkaClient.MAX_ITEMS_PER_REQUEST) {
            List<ExtractedPost> chunk = pending.subList(from,
                Math.min(from + BatchVezilkaClient.MAX_ITEMS_PER_REQUEST, pending.size()));
            try {
                DonationResponse response = vezilkaClient.submitTextDonations(chunk.stream()
                    .map(post -> new TextDonationItem(post.getSourceUrl(), post.getContent(), null, "mk", null))
                    .toList());
                applyResults(chunk, response);
                batch.setSubmittedAt(LocalDateTime.now());
            } catch (VezilkaIntegrationException exception) {
                batch.setNextRetryAt(exception.getRetryAt());
                boolean anyProcessed = batch.getPosts().stream().anyMatch(post -> post.getDonationStatus() != null);
                batch.setStatus(anyProcessed ? DonationStatus.SUBMITTED : DonationStatus.APPROVED);
                updateReference(batch);
                donationBatchRepository.save(batch);
                if (!anyProcessed) {
                    throw exception;
                }
                log.warn("Donation batch {} has pending items; retry after {}.", batch.getId(), batch.getNextRetryAt());
                return batch;
            }
        }
        updateReference(batch);
        batch.setStatus(batch.getPosts().stream().anyMatch(post -> post.getDonationStatus() == DonationStatus.ACCEPTED)
            ? DonationStatus.ACCEPTED : DonationStatus.REJECTED);
        return donationBatchRepository.save(batch);
    }

    private void applyResults(List<ExtractedPost> chunk, DonationResponse response) {
        if (response == null || response.results() == null || response.results().size() != chunk.size()
            || response.results().stream().anyMatch(result -> result == null || !result.isWellFormed())) {
            throw new VezilkaIntegrationException("Vezilka returned incomplete or invalid item results.");
        }
        for (int index = 0; index < chunk.size(); index++) {
            DonationItemResult result = response.results().get(index);
            ExtractedPost post = chunk.get(index);
            post.setVezilkaId(result.id());
            post.setDonationStatus(result.isAccepted() ? DonationStatus.ACCEPTED : DonationStatus.REJECTED);
            post.setRejectionReason(result.isAccepted() ? null : result.describeRejection());
        }
        extractedPostService.saveAll(chunk);
    }

    private void updateReference(DonationBatch batch) {
        batch.setVezilkaReference(batch.getPosts().stream()
            .filter(post -> post.getDonationStatus() == DonationStatus.ACCEPTED)
            .map(ExtractedPost::getVezilkaId)
            .filter(Objects::nonNull) // deduped posts are accepted without an id
            .findFirst().orElse(null));
    }

    private DonationBatch getForUpdate(Long id) {
        return donationBatchRepository.findForUpdate(id)
            .orElseThrow(() -> new DonationBatchNotFoundException(id));
    }
}
