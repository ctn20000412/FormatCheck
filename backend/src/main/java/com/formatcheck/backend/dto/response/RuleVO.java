package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.RuleItem;

import java.util.List;

public record RuleVO(
        String sessionId,
        TaskStatus status,
        boolean confirmed,
        List<RuleItem> rules
) {
}
