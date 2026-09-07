package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.exception.InvalidSessionStateException;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.repository.ExtractionSessionRepository;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtractionSessionServiceImpl implements ExtractionSessionService {
    private final ExtractionSessionRepository extractionSessionRepository;

    public ExtractionSessionServiceImpl(ExtractionSessionRepository extractionSessionRepository) {
        this.extractionSessionRepository = extractionSessionRepository;
    }

    @Override
    public List<ExtractionSession> findAll() {
        return extractionSessionRepository.findAll();
    }

    @Override
    public Optional<ExtractionSession> findById(Long id) {
        return extractionSessionRepository.findById(id);
    }

    @Override
    public ExtractionSession create(ExtractionSession session) {
        return extractionSessionRepository.save(session);
    }

    @Override
    @Transactional
    public ExtractionSession start(Long id) {
        ExtractionSession session = extractionSessionRepository.findForUpdate(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
        if (session.getStatus() != SessionStatus.CREATED && session.getStatus() != SessionStatus.PAUSED) {
            throw new InvalidSessionStateException(id, session.getStatus());
        }
        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(LocalDateTime.now());
        session.setFinishedAt(null);
        session.setExecutionNumber(session.getExecutionNumber() + 1);
        return extractionSessionRepository.save(session);
    }

    @Override
    @Transactional
    public ExtractionSession stop(Long id) {
        ExtractionSession session = extractionSessionRepository.findForUpdate(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
        if (session.getStatus() != SessionStatus.RUNNING) {
            throw new InvalidSessionStateException(id, session.getStatus());
        }
        session.setStatus(SessionStatus.PAUSED);
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession complete(Long id) {
        ExtractionSession session = getOrThrow(id);
        if (session.getStatus() != SessionStatus.RUNNING) {
            throw new InvalidSessionStateException(id, session.getStatus());
        }
        session.setStatus(SessionStatus.COMPLETED);
        session.setFinishedAt(LocalDateTime.now());
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession fail(Long id) {
        ExtractionSession session = getOrThrow(id);
        session.setStatus(SessionStatus.FAILED);
        session.setFinishedAt(LocalDateTime.now());
        return extractionSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void finishExecution(Long id, long executionNumber, boolean successful) {
        extractionSessionRepository.finishExecution(id, executionNumber,
            successful ? SessionStatus.COMPLETED : SessionStatus.FAILED,
            LocalDateTime.now(), SessionStatus.RUNNING);
    }

    private ExtractionSession getOrThrow(Long id) {
        return extractionSessionRepository
            .findById(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
    }
}
