package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;

public record RuleImportVO(
        String sessionId,
        String finalRuleFileName,
        boolean validated,
        TaskStatus status
) {
}
