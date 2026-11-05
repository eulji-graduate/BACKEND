package com.module06.backend.identity.member.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.member.application.usecase.GetMemberDetailUseCase;
import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberTeamUpdateIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private GetMemberDetailUseCase getDetail;
    @MockitoBean private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void seed() {
        sql("INSERT INTO company (id,code,name) VALUES (901,'TEAM-TEST','Demo')");
        sql("INSERT INTO team (id,company_id,name) VALUES (911,901,'Old'),(912,901,'New')");
        sql("INSERT INTO position (id,company_id,name,authority) VALUES (921,901,'Member','MEMBER')");
        sql("MERGE INTO role (id,name) KEY(id) VALUES (2,'None')");
        sql("INSERT INTO role (id,company_id,team_id,name) VALUES (931,901,911,'Old role'),(932,901,912,'New role')");
        sql("""
                INSERT INTO member (id,company_id,team_id,position_id,role_id,email,password_hash,name,authority,is_admin,status)
                VALUES (941,901,911,921,931,'member@example.test','hash','Target','MEMBER',FALSE,'ACTIVE')
                """);
    }

    @ParameterizedTest
    @CsvSource({"OWNER,false", "MEMBER,true", "LEADER,true"})
    void authorizedTeamMovePersistsAndRequeries(String role, boolean admin) throws Exception {
        mvc.perform(patch("/api/members/941").with(authentication(auth(role,admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\",\"jobPositionId\":921,\"teamId\":912,\"roleId\":932}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(912));
        em.flush();
        em.clear();
        var result = getDetail.getDetail(901L,941L);
        assertThat(result.teamId()).isEqualTo(912L);
        assertThat(result.teamName()).isEqualTo("New");
        assertThat(result.roleId()).isEqualTo(932L);
        verify(refreshTokenStore).revokeAllByMember(941L);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER,false", "LEADER,false"})
    void ordinaryMembersCannotMoveTeam(String role, boolean admin) throws Exception {
        mvc.perform(patch("/api/members/941").with(authentication(auth(role,admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\",\"jobPositionId\":921,\"teamId\":912}"))
                .andExpect(status().isForbidden());
        assertThat(getDetail.getDetail(901L,941L).teamId()).isEqualTo(911L);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER", "LEADER"})
    void movingLeaderIsRejectedAndBothTeamsRemainUnchanged(String newRole) throws Exception {
        sql("UPDATE member SET authority='LEADER' WHERE id=941");
        sql("UPDATE team SET leader_member_id=941 WHERE id=911");
        mvc.perform(patch("/api/members/941").with(authentication(auth("OWNER",false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\""+newRole+"\",\"jobPositionId\":921,\"teamId\":912}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AU-051"));
        em.flush(); em.clear();
        assertThat(((Number) em.createNativeQuery("SELECT leader_member_id FROM team WHERE id=911")
                .getSingleResult()).longValue()).isEqualTo(941L);
        assertThat(em.createNativeQuery("SELECT leader_member_id FROM team WHERE id=912").getSingleResult()).isNull();
        assertThat(getDetail.getDetail(901L,941L).teamId()).isEqualTo(911L);
        assertThat(getDetail.getDetail(901L,941L).roleId()).isEqualTo(931L);
        verifyNoInteractions(refreshTokenStore);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER", "LEADER"})
    void leaderCanStayLeaderAndMemberCanFillVacancy(String currentRole) throws Exception {
        if (currentRole.equals("LEADER")) {
            sql("UPDATE member SET authority='LEADER' WHERE id=941");
            sql("UPDATE team SET leader_member_id=941 WHERE id=911");
        }
        sql("INSERT INTO position (id,company_id,name,authority) VALUES (922,901,'Lead','LEADER')");
        mvc.perform(patch("/api/members/941").with(authentication(auth("OWNER",false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LEADER\",\"jobPositionId\":922,\"roleId\":931}"))
                .andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(((Number)em.createNativeQuery("SELECT leader_member_id FROM team WHERE id=911")
                .getSingleResult()).longValue()).isEqualTo(941L);
        var member = getDetail.getDetail(901L,941L);
        assertThat(member.role().name()).isEqualTo("LEADER");
        assertThat(member.jobPositionId()).isEqualTo(922L);
        verify(refreshTokenStore).revokeAllByMember(941L);
    }

    @Test
    void cannotDemoteLeaderWithinSameTeam() throws Exception {
        sql("UPDATE member SET authority='LEADER' WHERE id=941");
        sql("UPDATE team SET leader_member_id=941 WHERE id=911");
        mvc.perform(patch("/api/members/941").with(authentication(auth("OWNER",false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\",\"jobPositionId\":921}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AU-051"));
        em.flush(); em.clear();
        assertThat(getDetail.getDetail(901L,941L).role().name()).isEqualTo("LEADER");
        assertThat(((Number)em.createNativeQuery("SELECT leader_member_id FROM team WHERE id=911")
                .getSingleResult()).longValue()).isEqualTo(941L);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void cannotMoveMemberIntoOccupiedLeaderSeat() throws Exception {
        sql("""
                INSERT INTO member (id,company_id,team_id,position_id,role_id,email,password_hash,name,authority,is_admin,status)
                VALUES (942,901,912,921,932,'leader@example.test','hash','Leader','LEADER',FALSE,'ACTIVE')
                """);
        sql("UPDATE team SET leader_member_id=942 WHERE id=912");
        mvc.perform(patch("/api/members/941").with(authentication(auth("OWNER",false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LEADER\",\"jobPositionId\":921,\"teamId\":912,\"roleId\":932}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AU-029"));
        em.flush(); em.clear();
        assertThat(getDetail.getDetail(901L,941L).teamId()).isEqualTo(911L);
        assertThat(getDetail.getDetail(901L,941L).roleId()).isEqualTo(931L);
        assertThat(getDetail.getDetail(901L,941L).role().name()).isEqualTo("MEMBER");
        assertThat(getDetail.getDetail(901L,942L).role().name()).isEqualTo("LEADER");
        assertThat(((Number)em.createNativeQuery("SELECT leader_member_id FROM team WHERE id=912")
                .getSingleResult()).longValue()).isEqualTo(942L);
        verifyNoInteractions(refreshTokenStore);
    }

    private TestingAuthenticationToken auth(String role, boolean admin) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
        if (admin) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return new TestingAuthenticationToken(new AuthPrincipal(999L,901L,role,admin,null),null,authorities);
    }
    private void sql(String query) { em.createNativeQuery(query).executeUpdate(); }
}
