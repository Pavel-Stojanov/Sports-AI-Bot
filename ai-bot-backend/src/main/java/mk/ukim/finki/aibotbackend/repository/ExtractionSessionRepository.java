package mk.ukim.finki.aibotbackend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractionSessionRepository extends JpaRepository<ExtractionSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExtractionSession s where s.id = :id")
    Optional<ExtractionSession> findForUpdate(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ExtractionSession s set s.status = :status, s.finishedAt = :finishedAt
        where s.id = :id and s.executionNumber = :executionNumber and s.status = :running
        """)
    int finishExecution(Long id, long executionNumber, SessionStatus status,
                        LocalDateTime finishedAt, SessionStatus running);
}
