package com.module06.backend.migration;

import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway 없이 최종 SQL만 적용해 엔티티와 시연 데이터의 정합성을 검증한다. */
@Tag("local-sql")
@Testcontainers
@Import(MigrationSchemaValidationTest.ExcludeArchitectureFixtureEntities.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Sql(scripts = "/db/local/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class LocalSqlInitializationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("module06")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/local/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void initializesWithoutFlywayAndMatchesEntities() {
        // 컨텍스트 부팅 자체가 Hibernate validate 검증이다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `role` WHERE id IN (1, 2) "
                + "AND company_id IS NULL AND team_id IS NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void seedsUsableDemoCredentialsAndAdminPersona() {
        var hashes = jdbc.queryForList("SELECT password_hash FROM member", String.class);
        assertThat(hashes).allSatisfy(hash ->
                assertThat(passwordEncoder.matches("Demo1234!", hash)).isTrue());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE id = 3 "
                + "AND authority = 'MEMBER' AND is_admin = TRUE AND status = 'WAITING'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = 8", String.class))
                .isEqualTo("WAITING");
    }

    @Test
    void seedsCurrentSubscriptionAndConsistentPendingHandovers() {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE()", LocalDate.class);
        LocalDate periodStart = jdbc.queryForObject(
                "SELECT current_period_start FROM subscription WHERE company_id = 1", LocalDate.class);
        LocalDate periodEnd = jdbc.queryForObject(
                "SELECT current_period_end FROM subscription WHERE company_id = 1", LocalDate.class);
        assertThat(periodStart).isBeforeOrEqualTo(today);
        assertThat(periodEnd).isAfter(today);
        assertThat(jdbc.queryForObject("SELECT status FROM subscription WHERE company_id = 1", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = 301", String.class))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = 302", String.class))
                .isEqualTo("REASSIGNED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_item i JOIN `action` a "
                + "ON a.id = i.action_id WHERE i.handover_id = 302 "
                + "AND i.reassignee_id = a.assignee_member_id AND i.committed_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }
}
