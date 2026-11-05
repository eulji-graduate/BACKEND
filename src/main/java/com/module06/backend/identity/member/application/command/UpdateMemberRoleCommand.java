package com.module06.backend.identity.member.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 구성원 상세 — 역할·직급 변경(§7-4). {@code actingMemberId} 는 본인 변경 차단(CANNOT_MODIFY_SELF)
 * 검사에만 쓰고 저장하지 않는다.
 *
 * <p>{@code teamId}가 null이면 기존 팀을 유지한다. 역할 생략은 팀 이동 시 "없음",
 * 그 외에는 기존 역할 유지로 처리한다.
 */
public record UpdateMemberRoleCommand(
        Long companyId,
        Long actingMemberId,
        Long targetMemberId,
        Authority role,
        Long jobPositionId,
        Long roleId,
        Long teamId
) {
    public UpdateMemberRoleCommand(Long companyId, Long actingMemberId, Long targetMemberId,
                                   Authority role, Long jobPositionId, Long roleId) {
        this(companyId, actingMemberId, targetMemberId, role, jobPositionId, roleId, null);
    }
}
