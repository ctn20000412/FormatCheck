package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.AgentProvider;

public record AgentProviderVO(
        AgentProvider provider,
        boolean enabled,
        String defaultModel
) {
}
