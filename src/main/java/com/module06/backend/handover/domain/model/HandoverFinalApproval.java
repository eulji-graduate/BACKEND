package com.module06.backend.handover.domain.model;

/** The same readiness rule drives the write-side invariant and viewer-specific response. */
public record HandoverFinalApproval(
        boolean canFinalize,
        boolean requiresIntermediateApproval,
        BlockReason finalizeBlockReason
) {
    public enum BlockReason {
        OWNER_REQUIRED,
        INTERMEDIATE_APPROVAL_REQUIRED,
        ALREADY_FINALIZED,
        ALREADY_REJECTED
    }

    public static boolean isReady(HandoverStatus status, boolean currentTeamLeader) {
        return status == HandoverStatus.REASSIGNED
                || (status == HandoverStatus.SUBMITTED && currentTeamLeader);
    }

    public static HandoverFinalApproval evaluate(HandoverStatus status, boolean currentTeamLeader, boolean owner) {
        java.util.Objects.requireNonNull(status, "status");
        boolean needsIntermediate = status == HandoverStatus.SUBMITTED && !currentTeamLeader;
        if (status == HandoverStatus.FINALIZED) {
            return new HandoverFinalApproval(false, false, BlockReason.ALREADY_FINALIZED);
        }
        if (status == HandoverStatus.REJECTED) {
            return new HandoverFinalApproval(false, false, BlockReason.ALREADY_REJECTED);
        }
        if (!owner) return new HandoverFinalApproval(false, needsIntermediate, BlockReason.OWNER_REQUIRED);
        if (needsIntermediate) {
            return new HandoverFinalApproval(false, true, BlockReason.INTERMEDIATE_APPROVAL_REQUIRED);
        }
        return new HandoverFinalApproval(isReady(status, currentTeamLeader), false, null);
    }
}
