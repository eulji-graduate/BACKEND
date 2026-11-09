package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverFinalApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverSummaryResponse(
        Long id,
        Long writerMemberId,
        String writerName,
        String writerPosition,
        Long teamId,
        HandoverType handoverType,
        HandoverStatus status,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        int itemCount,
        int reassignRequiredCount,
        int reassignedCount,
        LocalDateTime finalizedAt,
        String finalApproverName,
        @Schema(description = "조회자가 현재 이 신청을 최종 승인할 수 있는지. 실제 승인 요청에서 다시 검증합니다.")
        boolean canFinalize,
        @Schema(description = "현재 중간 승인이 더 필요한지. 중간 승인 완료 또는 팀장 직접 승인 경로이면 false입니다.")
        boolean requiresIntermediateApproval,
        @Schema(description = "최종 승인 불가 사유. 승인 가능하면 null입니다.")
        HandoverFinalApproval.BlockReason finalizeBlockReason
) {

    public static HandoverSummaryResponse from(GetHandoverListUseCase.HandoverSummary summary, HandoverFinalApproval approval) {
        return new HandoverSummaryResponse(
                summary.id(),
                summary.writerMemberId(),
                summary.writerName(),
                summary.writerPosition(),
                summary.teamId(),
                summary.handoverType(),
                summary.status(),
                summary.leaveStartAt(),
                summary.leaveEndAt(),
                summary.lastWorkingDay(),
                summary.itemCount(),
                summary.reassignRequiredCount(),
                summary.reassignedCount(),
                summary.finalizedAt(),
                summary.finalApproverName(),
                approval.canFinalize(),
                approval.requiresIntermediateApproval(),
                approval.finalizeBlockReason()
        );
    }
}
