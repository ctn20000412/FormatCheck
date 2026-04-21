package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.CheckIssue;

import java.util.List;
import java.util.Map;

public record CheckResultVO(
        String sessionId,
        TaskStatus status,
        Map<String, Integer> summary,
        List<CheckIssue> issues,
        List<ArtifactVO> reportFiles
) {
}
