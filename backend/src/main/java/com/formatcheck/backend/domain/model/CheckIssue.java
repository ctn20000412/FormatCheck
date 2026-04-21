package com.formatcheck.backend.domain.model;

import java.util.List;

public record CheckIssue(
        String id,
        Location location,
        String ruleId,
        String ruleName,
        String sourceText,
        List<String> applicableSections,
        List<String> checkObject,
        String severity,
        String reason,
        String suggestion,
        Object expectedValue,
        Object actualValue
) {
    public record Location(Integer page, Integer paragraphIndex, String heading) {
    }
}
