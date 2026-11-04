package com.module06.backend.migration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.member.application.usecase.GetMembersUseCase;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.identity.auth.application.service.AuthService;
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.application.service.HandoverService;
import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.domain.model.HandoverStatus;
import tools.jackson.databind.ObjectMapper;

/** Flyway 없이 최종 SQL만 적용해 엔티티와 시연 데이터의 정합성을 검증한다. */
@Tag("local-sql")
@Testcontainers
@Import(MigrationSchemaValidationTest.ExcludeArchitectureFixtureEntities.class)
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
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

    @Autowired
    GetMembersUseCase members;

    @Autowired
    GetStorageOverviewUseCase storage;

    @Autowired
    DeleteProjectStorageUseCase deleteStorage;

    @Autowired
    AuthService auth;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 로그인 검증은 실제 MySQL/BCrypt로 하고, 세션 저장만 외부 Redis 없이 격리한다.
    @MockitoBean
    RefreshTokenStore refreshTokens;

    @Autowired
    HandoverService handovers;

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
        assertThat(hashes).hasSize(10).allSatisfy(hash ->
                assertThat(hash).matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"));
        // 팀에서 별도로 전달받은 비밀번호로 실제 일치 검증도 선택 실행할 수 있다.
        String demoPassword = System.getenv("DEMO_LOGIN_PASSWORD");
        if (demoPassword != null && !demoPassword.isBlank()) {
            assertThat(hashes).allSatisfy(hash ->
                    assertThat(passwordEncoder.matches(demoPassword, hash)).isTrue());
        }
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

    @Test
    void seedsFrontendMemberAndRoomPersonas() {
        var page = members.getMembers(1L, MemberListFilter.ALL, null, 0, 10);
        assertThat(page.totalElements()).isEqualTo(10);
        assertThat(page.content()).filteredOn(member -> member.memberId().equals(1L))
                .singleElement().satisfies(owner -> {
                    assertThat(owner.positionName()).isEqualTo("대표");
                    assertThat(owner.teamName()).isNull();
                });
        assertThat(page.content()).filteredOn(member -> member.memberId().equals(3L))
                .singleElement().satisfies(admin -> {
                    assertThat(admin.isAdmin()).isTrue();
                    assertThat(admin.pendingHandoverType()).isEqualTo(PendingHandoverType.VACATION);
                });
        assertThat(page.content()).filteredOn(member -> member.memberId().equals(8L))
                .singleElement().satisfies(member ->
                        assertThat(member.pendingHandoverType()).isEqualTo(PendingHandoverType.OFFBOARDING));
        assertThat(page.content()).filteredOn(member -> member.role().name().equals("LEADER"))
                .hasSize(4).allSatisfy(leader -> assertThat(leader.roleLabel()).isEqualTo("없음"));
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM team t JOIN member m "
                + "ON m.team_id = t.id AND m.authority = 'LEADER' "
                + "AND m.id = t.leader_member_id WHERE m.company_id = t.company_id "
                + "GROUP BY t.id", Integer.class)).hasSize(4).containsOnly(1);
        assertThat(jdbc.queryForList("SELECT CONCAT(name, ':', location) FROM meeting_room", String.class))
                .containsExactlyInAnyOrder("대회의실:3층 A동", "소회의실 A:3층 A동",
                        "소회의실 B:3층 B동", "화상회의실:본관 5층");
    }

    @Test
    void seedsThreeUnfinishedVacationItemsAndRelativeDates() {
        LocalDate initialized = initializedOn();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_item WHERE handover_id = 301", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_item i JOIN `action` a ON a.id = i.action_id "
                + "WHERE i.handover_id = 301 AND a.is_done = FALSE AND a.assignee_member_id = 3", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT is_done FROM `action` WHERE id = 103", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT DATE(leave_start_at) FROM handover WHERE id = 301", LocalDate.class))
                .isEqualTo(initialized.plusDays(3));
        assertThat(jdbc.queryForObject("SELECT DATE(leave_end_at) FROM handover WHERE id = 301", LocalDate.class))
                .isEqualTo(initialized.plusDays(7));
        assertThat(jdbc.queryForObject("SELECT DATE(intermediate_approved_at) FROM handover WHERE id = 302", LocalDate.class))
                .isEqualTo(initialized.minusDays(2));
        assertThat(jdbc.queryForObject("SELECT created_at <= intermediate_approved_at "
                + "FROM handover WHERE id = 302", Boolean.class)).isTrue();
        for (var expected : Map.of(101L, -1L, 102L, 2L, 103L, -10L, 107L, 8L).entrySet()) {
            assertThat(jdbc.queryForObject("SELECT due_date FROM `action` WHERE id = ?", LocalDate.class, expected.getKey()))
                    .isEqualTo(initialized.plusDays(expected.getValue()));
        }
        assertThat(jdbc.queryForObject("SELECT assignee_member_id FROM `action` WHERE id = 107", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void storageOverviewMatchesFrontendTotalsAndProjectRows() {
        var overview = storage.getOverview(1L);
        assertThat(overview.voiceGb()).isEqualByComparingTo("34.9");
        assertThat(overview.sttGb()).isEqualByComparingTo("6.8");
        assertThat(overview.voiceGb().add(overview.sttGb())).isEqualByComparingTo("41.7");
        assertThat(jdbc.queryForObject("SELECT storage_cap_bytes FROM company_storage_plan WHERE company_id = 1", Long.class))
                .isEqualTo(53_687_091_200L);
        var voice = Map.of("product-v2", 9_771_050_598L, "infra-migration", 11_703_785_882L,
                "marketing-q3", 7_301_444_403L, "hr-process", 4_831_838_208L, "ops-automation", 3_865_470_566L);
        var text = Map.of("product-v2", 1_503_238_554L, "infra-migration", 1_825_361_101L,
                "marketing-q3", 1_181_116_006L, "hr-process", 1_395_864_371L, "ops-automation", 1_395_864_371L);
        var age = Map.of("product-v2", 3L, "infra-migration", 8L, "marketing-q3", 5L,
                "hr-process", 130L, "ops-automation", 260L);
        assertThat(overview.projects()).hasSize(5).allSatisfy(project -> {
            assertThat(project.meetingCount()).isEqualTo(2);
            assertThat(project.lastRecordedAt()).isEqualTo(initializedOn().minusDays(age.get(project.tag())));
            assertThat(jdbc.queryForObject("SELECT SUM(u.used_bytes) FROM meeting_storage_usage u "
                    + "JOIN project p ON p.id = u.project_id WHERE p.tag = ?", Long.class, project.tag()))
                    .isEqualTo(voice.get(project.tag()));
            assertThat(jdbc.queryForObject("SELECT SUM(u.caption_bytes + u.transcript_bytes + u.summary_bytes) "
                    + "FROM meeting_text_storage_usage u JOIN project p ON p.id = u.project_id WHERE p.tag = ?",
                    Long.class, project.tag())).isEqualTo(text.get(project.tag()));
        });
    }

    @Test
    @Transactional
    void completedProjectCanBeClearedButActiveProjectIsProtected() {
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L, "product-v2"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MeteringErrorCode.MT_STORAGE_PROJECT_NOT_DONE));
        assertThat(storage.getOverview(1L).projects()).hasSize(5);

        deleteStorage.deleteByTag(1L, "hr-process");

        var after = storage.getOverview(1L);
        assertThat(after.projects()).hasSize(4).noneMatch(project -> project.tag().equals("hr-process"));
        assertThat(after.voiceGb()).isLessThan(new BigDecimal("34.9"));
        assertThat(after.sttGb()).isLessThan(new BigDecimal("6.8"));
    }

    @Test
    @Transactional
    void seededAccountsLoginAndWaitingAdminCanManageRooms() throws Exception {
        String password = System.getenv("DEMO_LOGIN_PASSWORD");
        assumeTrue(password != null && !password.isBlank(), "별도 공유된 시연 비밀번호를 환경변수로 제공할 때 실행");
        var owner = auth.login(new LoginCommand("ZDEMO", "ceo@zgroup.co.kr", password, false));
        var leader = auth.login(new LoginCommand("ZDEMO", "seojun@zgroup.co.kr", password, false));
        var admin = auth.login(new LoginCommand("ZDEMO", "hayun@zgroup.co.kr", password, false));
        assertThat(owner.landingPath()).isEqualTo("/owner");
        assertThat(leader.landingPath()).isEqualTo("/team");
        assertThat(admin.landingPath()).isEqualTo("/my");
        String bearer = "Bearer " + admin.accessToken();
        for (String path : new String[]{"/api/members", "/api/rooms", "/api/companies/me/storage"}) {
            mockMvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isOk());
        }
        var response = mockMvc.perform(post("/api/rooms").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"검증용 회의실\",\"location\":\"검증 환경\"}"))
                .andExpect(status().isCreated()).andReturn();
        long roomId = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data").path("meetingRoomId").asLong();
        assertThat(roomId).isPositive();
        mockMvc.perform(patch("/api/rooms/" + roomId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"location\":\"변경 위치\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/rooms/" + roomId).header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    private LocalDate initializedOn() {
        return jdbc.queryForObject("SELECT DATE(created_at) FROM company WHERE id = 1", LocalDate.class);
    }

    @Test
    @Transactional
    void vacationDemoCanCompleteThreeItemsAndBeFinalizedByOwner() {
        var leader = new AuthPrincipal(2L, 1L, "LEADER", false, 1L);
        var owner = new AuthPrincipal(1L, 1L, "OWNER", false, null);
        LocalDateTime now = LocalDateTime.now();
        for (Long actionId : new Long[]{101L, 102L, 106L}) {
            handovers.reassignItem(new ReassignItemCommand(301L, actionId, 4L, now, leader));
        }
        assertThat(handovers.complete(301L, leader, now).getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(handovers.finalize(301L, owner, now).getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = 3", String.class)).isEqualTo("VACATION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `action` WHERE id IN (101,102,106) "
                + "AND assignee_member_id = 4", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT assignee_member_id FROM `action` WHERE id = 103", Long.class))
                .isEqualTo(3L);
    }

    @Test
    @Transactional
    void offboardingDemoFinalizationRemovesWriterFromMemberList() {
        var owner = new AuthPrincipal(1L, 1L, "OWNER", false, null);
        assertThat(members.getMembers(1L, MemberListFilter.ALL, null, 0, 10).totalElements()).isEqualTo(10);

        assertThat(handovers.finalize(302L, owner, LocalDateTime.now()).getStatus())
                .isEqualTo(HandoverStatus.FINALIZED);

        assertThat(members.getMembers(1L, MemberListFilter.ALL, null, 0, 10).totalElements()).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM member WHERE id = 8", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT assignee_member_id FROM `action` WHERE id = 105", Long.class))
                .isEqualTo(6L);
    }
}
