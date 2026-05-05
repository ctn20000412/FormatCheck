package com.formatcheck.backend.service;

import java.util.List;
import java.util.Map;

public record AgentProcessResult(
        boolean success,
        String message,
        Map<String, Object> statistics,
        List<Map<String, Object>> issues,
        Map<String, Object> payload) {}
