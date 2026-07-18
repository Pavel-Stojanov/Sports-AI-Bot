package mk.ukim.finki.aibotbackend.repository;

import jakarta.transaction.Transactional;
import mk.ukim.finki.aibotbackend.config.JpaConfig;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.enums.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@Transactional
@Testcontainers
public class ExtractionSessionRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("aibot_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ExtractionSessionRepository extractionSessionRepository;

    private ExtractionSession session;

    @BeforeEach
    void setUp() {
        session = new ExtractionSession(SocialNetwork.REDDIT, "test session");
        session.getTargets().add(new ExtractionTarget(TargetType.FEED_URL, "https://example.com", session));
        session.getTargets().add(new ExtractionTarget(TargetType.KEYWORD, "фудбал", session));
        session = extractionSessionRepository.save(session);
    }

    @Test
    void testSaveCascadesTargets() {
        ExtractionSession found = extractionSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(found.getTargets()).hasSize(2);
        assertThat(found.getTargets().getFirst().getType()).isEqualTo(TargetType.FEED_URL);
    }

    @Test
    void testStatusUpdatePersists() {
        session.setStatus(SessionStatus.RUNNING);
        extractionSessionRepository.save(session);
        assertThat(extractionSessionRepository.findById(session.getId()).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.RUNNING);
    }

    @Test
    void testFindSessions() {
        assertThat(extractionSessionRepository.findAll())
            .extracting(ExtractionSession::getDescription)
            .contains("test session");
    }
}
