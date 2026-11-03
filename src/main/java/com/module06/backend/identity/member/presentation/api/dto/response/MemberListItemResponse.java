package com.module06.backend.identity.member.presentation.api.dto.response;

import java.time.LocalDate;

import com.module06.backend.identity.member.application.dto.MemberListItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberListItemResponse(
        Long memberId,
        String name,
        String teamName,
        String positionName,
        String role,
        boolean isAdmin,
        String roleLabel,
        String workStatus,
        LocalDate joinedOn,
        @Schema(description = "대기 중인 인수인계 신청 종류. 대기 신청이 없으면 null",
                allowableValues = {"VACATION", "OFFBOARDING"})
        String pendingHandoverType
) {

    public static MemberListItemResponse from(MemberListItem item) {
        return new MemberListItemResponse(
                item.memberId(), item.name(), item.teamName(), item.positionName(),
                item.role().name(), item.isAdmin(), item.roleLabel(),
                item.workStatus().name(), item.joinedOn(),
                item.pendingHandoverType() == null ? null : item.pendingHandoverType().name());
    }
}
