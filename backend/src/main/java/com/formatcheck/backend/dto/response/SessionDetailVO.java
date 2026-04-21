package com.formatcheck.backend.dto.response;

import com.formatcheck.backend.domain.enums.AgentProvider;
import com.formatcheck.backend.domain.enums.RuleSource;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record SessionDetailVO(
        String sessionId,
        String name,
        SessionType type,
        TaskStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer ruleVersion,
        String standardFileName,
        String targetFileName,
        RuleSource ruleSource,
        String errorMessage,
        AgentProvider agentProvider,
        String agentModel,
        List<ArtifactVO> artifacts
) {
}
