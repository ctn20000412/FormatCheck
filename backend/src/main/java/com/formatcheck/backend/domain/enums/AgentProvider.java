package com.formatcheck.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AgentProvider {
    KIMI,
    DEEPSEEK,
    CHATGPT,
    GEMINI,
    GLM;

    @JsonCreator
    public static AgentProvider fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AgentProvider.valueOf(value.trim().toUpperCase());
    }
}
