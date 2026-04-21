package com.formatcheck.backend.service;

import com.formatcheck.backend.domain.enums.AgentProvider;

import java.util.Map;

public record AgentExecutionOptions(
        AgentProvider provider,
        String model,
        String apiBaseUrl,
        String apiKey,
        Map<String, String> extraConfig
) {
}
