package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;

public record RuleConfirmVO(
        String sessionId,
        String finalRuleFileName,
        Integer ruleVersion,
        TaskStatus status
) {
}
