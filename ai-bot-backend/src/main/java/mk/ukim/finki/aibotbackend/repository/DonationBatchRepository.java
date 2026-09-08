package mk.ukim.finki.aibotbackend.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DonationBatchRepository extends JpaRepository<DonationBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DonationBatch b where b.id = :id")
    Optional<DonationBatch> findForUpdate(Long id);

    /** Batches in the status whose retry time has passed or was never set. */
    @Query("""
        select b.id from DonationBatch b
        where b.status = :status and (b.nextRetryAt is null or b.nextRetryAt <= :now)
        order by b.id
        """)
    List<Long> findDueIdsByStatus(DonationStatus status, Instant now);
}
