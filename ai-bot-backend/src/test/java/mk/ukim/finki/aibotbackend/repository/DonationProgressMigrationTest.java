package mk.ukim.finki.aibotbackend.repository;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DonationProgressMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void legacyFinishedBatchStaysClosedAndKeepsItsAcceptedPost() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .target("7").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO extraction_sessions (id, created_at, updated_at, social_network, status)
                VALUES (1, now(), now(), 'SPORTS_PORTAL_GOL', 'COMPLETED')
                """);
            statement.executeUpdate("""
                INSERT INTO donation_batches (id, created_at, updated_at, status)
                VALUES (1, now(), now(), 'ACCEPTED')
                """);
            statement.executeUpdate("""
                INSERT INTO extracted_posts (id, created_at, updated_at, session_id, version, donation_batch_id, vezilka_id)
                VALUES (1, now(), now(), 1, 0, 1, 'accepted-id'), (2, now(), now(), 1, 0, 1, NULL)
                """);
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
            try (var rows = statement.executeQuery("SELECT status FROM donation_batches WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ACCEPTED");
            }
            try (var rows = statement.executeQuery("SELECT donation_status, vezilka_id FROM extracted_posts ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ACCEPTED");
                assertThat(rows.getString(2)).isEqualTo("accepted-id");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
            }
        }
    }
}
