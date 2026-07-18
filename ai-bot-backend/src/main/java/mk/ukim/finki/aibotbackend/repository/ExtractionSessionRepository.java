package mk.ukim.finki.aibotbackend.repository;

import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractionSessionRepository extends JpaRepository<ExtractionSession, Long> {
    // The provided JpaRepository methods cover every query the session
    // services need — no custom queries required for the gol.mk bot.
}
