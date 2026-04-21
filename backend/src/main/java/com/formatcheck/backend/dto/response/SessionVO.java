package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;

import java.time.OffsetDateTime;

public record SessionVO(
        String sessionId,
        SessionType type,
        TaskStatus status,
        OffsetDateTime createdAt
) {
}
