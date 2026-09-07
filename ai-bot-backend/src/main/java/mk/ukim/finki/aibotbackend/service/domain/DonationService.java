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
     */
    DonationBatch submit(Long id);

    /**
     * Resends the unsent posts of every batch still left in SUBMITTED once its
     * retry delay has passed. Each batch commits in its own transaction, so a
     * failure in one batch cannot undo verdicts stored for another. Called
     * periodically by the {@code DonationStatusScheduler}.
     */
    void refreshSubmittedStatuses();
}
