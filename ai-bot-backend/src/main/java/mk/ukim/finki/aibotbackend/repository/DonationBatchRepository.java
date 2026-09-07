package mk.ukim.finki.aibotbackend.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonationBatchRepository extends JpaRepository<DonationBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DonationBatch b where b.id = :id")
    Optional<DonationBatch> findForUpdate(Long id);

    List<DonationBatch> findAllByStatus(DonationStatus status);
}
