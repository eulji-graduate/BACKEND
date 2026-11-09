package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverFinalApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HandoverResponse(
        Long id,
        Long writerMemberId,
        Long teamId,
        String teamNameSnap,
        HandoverType handoverType,
        HandoverStatus status,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        String writerNameSnap,
        String writerPositionSnap,
        String note,
        Long intermediateApproverId,
        String intermediateApproverNameSnap,
        LocalDateTime intermediateApprovedAt,
        String rejectReason,
        LocalDateTime finalizedAt,
        Long finalApproverId,
        String finalApproverNameSnap,
        Long version,
        List<HandoverItemResponse> items,
        @Schema(description = "조회자가 현재 이 신청을 최종 승인할 수 있는지. 실제 승인 요청에서 다시 검증합니다.")
        boolean canFinalize,
        @Schema(description = "현재 중간 승인이 더 필요한지. 중간 승인 완료 또는 팀장 직접 승인 경로이면 false입니다.")
        boolean requiresIntermediateApproval,
        @Schema(description = "최종 승인 불가 사유. 승인 가능하면 null입니다.")
        HandoverFinalApproval.BlockReason finalizeBlockReason
) {

    public static HandoverResponse from(Handover handover, HandoverFinalApproval approval) {
        return new HandoverResponse(
                handover.getId(),
                handover.getWriterMemberId(),
                handover.getTeamId(),
                handover.getTeamNameSnap(),
                handover.getHandoverType(),
                handover.getStatus(),
                handover.getLeaveStartAt(),
                handover.getLeaveEndAt(),
                handover.getLastWorkingDay(),
                handover.getWriterNameSnap(),
                handover.getWriterPositionSnap(),
                handover.getNote(),
                handover.getIntermediateApproverId(),
                handover.getIntermediateApproverNameSnap(),
                handover.getIntermediateApprovedAt(),
                handover.getRejectReason(),
                handover.getFinalizedAt(),
                handover.getFinalApproverId(),
                handover.getFinalApproverNameSnap(),
                handover.getVersion(),
                handover.getItems().stream()
                        .map(HandoverItemResponse::from)
                        .toList(),
                approval.canFinalize(),
                approval.requiresIntermediateApproval(),
                approval.finalizeBlockReason()
        );
    }
}
