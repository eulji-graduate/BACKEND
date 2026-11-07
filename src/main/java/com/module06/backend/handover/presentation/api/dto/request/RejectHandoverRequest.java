package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RejectHandoverRequest(
        @Schema(description = "반려 사유. 필수, 최대 200자")
        @NotBlank @Size(max = 200) String reason
) {

    public RejectHandoverCommand toCommand(Long handoverId, AuthPrincipal requester) {
        return new RejectHandoverCommand(handoverId, reason, requester);
    }
}
