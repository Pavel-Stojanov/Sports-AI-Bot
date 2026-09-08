package mk.ukim.finki.aibotbackend.service.domain;

import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;

/**
 * Domain service for donation batches and their journey to doniraj.vezilka.ai.
 */
public interface DonationService {
    List<DonationBatch> findAll();

    Optional<DonationBatch> findById(Long id);

    /**
     * Creates a DRAFT batch from already-extracted posts.
     * Throws {@code PostNotFoundException} when an id does not exist.
     */
    DonationBatch createBatch(List<Long> postIds);

    /**
     * Transitions a DRAFT batch to APPROVED — a human has reviewed the
     * content and confirmed it should be donated.
     */
    DonationBatch approve(Long id);

    /**
     * Submits an APPROVED batch to doniraj.vezilka.ai via the
     * {@code VezilkaClient}, one item per post, stamps {@code submittedAt}
     * and records Vezilka's verdict on every post.
     *
     * <p>Vezilka moderates automatically and answers immediately, so a batch
     * that is donated in full settles straight to ACCEPTED (at least one post
     * entered the corpus) or REJECTED. A batch large enough to need several
     * requests can be cut short — by the hourly rate limit, for instance — and
     * is left SUBMITTED with the verdicts received so far, for
     * {@link #refreshSubmittedStatuses()} to settle.</p>
     *
     * <p>No database lock is held while Vezilka answers: the batch is leased in
     * one short transaction, each chunk's verdicts commit on their own, and the
     * batch settles in a final one. A failure the API called permanent, or the
     * fifth failed attempt, moves the batch to FAILED with the reason stored;
     * a person may submit a FAILED batch again.</p>
     */
    DonationBatch submit(Long id);

    /**
     * Resends the unsent posts of every SUBMITTED batch whose retry time has
     * passed. Batches are leased and committed one at a time, so a failure in
     * one batch cannot undo verdicts stored for another. Called periodically
     * by the {@code DonationStatusScheduler}.
     */
    void refreshSubmittedStatuses();
}
