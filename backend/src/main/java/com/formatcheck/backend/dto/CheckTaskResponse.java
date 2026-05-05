package com.formatcheck.backend.dto;

import java.util.List;
import java.util.Map;

public record CheckTaskResponse(
        boolean success,
        String message,
        String workflow,
        String resultFolder,
        FileLink formatRule,
        FileLink analysisDoc,
        FileLink annotatedDoc,
        Map<String, Object> statistics,
        Map<String, Object> compareResult,
        List<Map<String, Object>> issues) {}
