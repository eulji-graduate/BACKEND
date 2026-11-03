package com.module06.backend.handover.presentation.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.handover.application.port.out.MemberStatusPort;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 JWT 필터와 서비스 권한 검사를 함께 통과시켜 최종/중간 결재 경계를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class HandoverApprovalSecurityTest {

    private static final long WRITER = 1L;
    private static final long LEADER = 9L;
    private static final long COMPANY = 100L;
    private static final long TEAM = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtTokenProvider tokens;
    @MockitoBean
    HandoverRepository repository;
    @MockitoBean
    OrgQueryPort organization;
    @MockitoBean
    MemberStatusPort memberStatus;

    @ParameterizedTest
    @CsvSource({"MEMBER, true", "LEADER, true", "MEMBER, false", "LEADER, false"})
    void finalApprovalRejectsNonOwnerAtHttpBoundary(String role, boolean admin) throws Exception {
        mockMvc.perform(patch("/api/handovers/1000/finalize")
                        .header("Authorization", bearer(role, admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("Z-002"));
        verifyNoInteractions(repository, memberStatus);
    }

    @ParameterizedTest
    @CsvSource({"false, finalize", "true, finalize", "false, reject", "true, reject"})
    void ownerCanMakeFinalDecisionRegardlessOfAdminFlag(boolean admin, String action) throws Exception {
        Handover handover = stubHandover(true);
        when(organization.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(WRITER));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        if (action.equals("finalize")) {
            when(organization.findMember(LEADER)).thenReturn(new OrgQueryPort.MemberSnapshot(LEADER, "Owner", "대표"));
        }

        mockMvc.perform(patch("/api/handovers/1000/" + action)
                        .header("Authorization", bearer("OWNER", admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"보완 필요\"}"))
                .andExpect(status().isOk());

        assertThat(handover.getStatus()).isEqualTo(action.equals("finalize")
                ? HandoverStatus.FINALIZED : HandoverStatus.REJECTED);
        if (action.equals("finalize")) {
            verify(memberStatus).toVacation(WRITER);
        } else {
            verify(memberStatus).restoreActive(WRITER);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leaderCannotRejectFinalStageEvenWithAdminFlag(boolean admin) throws Exception {
        Handover handover = stubHandover(true);
        mockMvc.perform(patch("/api/handovers/1000/reject")
                        .header("Authorization", bearer("LEADER", admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"보완 필요\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HO-026"));
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        verify(repository, never()).save(any());
        verifyNoInteractions(memberStatus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentLeaderKeepsIntermediateRejection(boolean admin) throws Exception {
        stubHandover(false);
        when(organization.findTeamLeaderId(TEAM)).thenReturn(LEADER);
        when(organization.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(WRITER));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(patch("/api/handovers/1000/reject")
                        .header("Authorization", bearer("LEADER", admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"보완 필요\"}"))
                .andExpect(status().isOk());
        verify(memberStatus).restoreActive(WRITER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void memberCannotRejectEvenWithAdminFlag(boolean admin) throws Exception {
        mockMvc.perform(patch("/api/handovers/1000/reject")
                        .header("Authorization", bearer("MEMBER", admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"보완 필요\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, memberStatus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"finalize", "reject"})
    void ownerCannotDecideOnAnotherCompanyRequest(String action) throws Exception {
        Handover handover = stubHandover(true);
        when(organization.findMemberIdsByCompany(COMPANY)).thenReturn(List.of());
        mockMvc.perform(patch("/api/handovers/1000/" + action)
                        .header("Authorization", bearer("OWNER", false))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"보완 필요\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HO-026"));
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        verify(repository, never()).save(any());
        verifyNoInteractions(memberStatus);
    }

    private String bearer(String role, boolean admin) {
        return "Bearer " + tokens.createAccessToken(new AuthPrincipal(LEADER, COMPANY, role, admin, TEAM));
    }

    private Handover stubHandover(boolean reassigned) {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Writer", "사원",
                NOW.plusDays(1), NOW.plusDays(7), List.of());
        if (reassigned) {
            handover.complete(LEADER, "Leader", NOW);
        }
        when(repository.findById(1000L)).thenReturn(Optional.of(handover));
        return handover;
    }
}
