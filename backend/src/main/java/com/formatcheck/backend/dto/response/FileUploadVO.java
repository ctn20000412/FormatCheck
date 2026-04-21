package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;

public record FileUploadVO(
        String sessionId,
        String fileName,
        TaskStatus status
) {
}
