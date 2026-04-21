package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;

public record TaskVO(
        String sessionId,
        TaskStatus status,
        String message
) {
}
