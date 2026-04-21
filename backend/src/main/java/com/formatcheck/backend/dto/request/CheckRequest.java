package com.formatcheck.backend.dto.request;

import com.formatcheck.backend.domain.enums.AgentProvider;
import jakarta.validation.constraints.NotBlank;

public record CheckRequest(
        @NotBlank(message = "sessionId 不能为空") String sessionId,
        @NotBlank(message = "ruleSessionId 不能为空") String ruleSessionId,
        String checkLevel,
        AgentProvider provider,
        String model
) {
}
