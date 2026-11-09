package com.module06.backend.handover.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import com.module06.backend.global.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HandoverFinalApprovalTest {
    static Stream<Arguments> cases() {
        return Stream.of(HandoverStatus.values()).flatMap(status ->
                Stream.of(false, true).flatMap(leader ->
                        Stream.of(false, true).map(owner -> Arguments.of(status, leader, owner))));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void responseReadinessMatchesActualDomainTransition(HandoverStatus status, boolean leader, boolean owner) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 0);
        Handover handover = Handover.createVacation(1L, 10L, "Writer", "사원", now.plusDays(1), now.plusDays(7), List.of());
        if (status == HandoverStatus.REASSIGNED || status == HandoverStatus.FINALIZED) handover.complete(9L, "Leader", now);
        if (status == HandoverStatus.FINALIZED) handover.finalizeApproval(9L, "Owner", now);
        if (status == HandoverStatus.REJECTED) handover.reject("보완 필요");

        HandoverFinalApproval result = HandoverFinalApproval.evaluate(status, leader, owner);
        boolean ready = status == HandoverStatus.REASSIGNED || (status == HandoverStatus.SUBMITTED && leader);
        assertThat(result.canFinalize()).isEqualTo(owner && ready);
        assertThat(result.requiresIntermediateApproval()).isEqualTo(status == HandoverStatus.SUBMITTED && !leader);
        if (status == HandoverStatus.FINALIZED) {
            assertThat(result.finalizeBlockReason()).isEqualTo(HandoverFinalApproval.BlockReason.ALREADY_FINALIZED);
        } else if (status == HandoverStatus.REJECTED) {
            assertThat(result.finalizeBlockReason()).isEqualTo(HandoverFinalApproval.BlockReason.ALREADY_REJECTED);
        } else if (!owner) {
            assertThat(result.finalizeBlockReason()).isEqualTo(HandoverFinalApproval.BlockReason.OWNER_REQUIRED);
        } else if (!ready) {
            assertThat(result.finalizeBlockReason()).isEqualTo(HandoverFinalApproval.BlockReason.INTERMEDIATE_APPROVAL_REQUIRED);
        } else {
            assertThat(result.finalizeBlockReason()).isNull();
        }
        // The domain transition guards readiness; caller identity is enforced by the service and HTTP boundary.
        if (ready) {
            handover.finalizeApproval(9L, "Owner", now, leader);
            assertThat(handover.getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        } else {
            assertThatThrownBy(() -> handover.finalizeApproval(9L, "Owner", now, leader)).isInstanceOf(BusinessException.class);
        }
    }
}
