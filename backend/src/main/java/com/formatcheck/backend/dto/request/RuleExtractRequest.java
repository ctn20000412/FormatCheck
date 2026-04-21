package com.formatcheck.backend.dto.request;

import com.formatcheck.backend.domain.enums.AgentProvider;
import jakarta.validation.constraints.NotBlank;

public record RuleExtractRequest(
        @NotBlank(message = "sessionId 不能为空") String sessionId,
        String schemaVersion,
        AgentProvider provider,
        String model
) {
}
