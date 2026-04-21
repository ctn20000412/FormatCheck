package com.formatcheck.backend.service;

import com.formatcheck.backend.domain.model.CheckIssue;
import com.formatcheck.backend.domain.model.RuleItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface AgentClient {

    ExtractionResult extractRules(String sessionId, Path standardFilePath, Path outputDir, String promptText, String schemaVersion, AgentExecutionOptions options);

    CheckExecutionResult executeCheck(String sessionId, Path targetFilePath, Path ruleFilePath, Path outputDir, String promptText, String checkLevel, AgentExecutionOptions options);

    record ExtractionResult(List<RuleItem> rules, String confidenceNote, List<String> unidentifiedItems) {
    }

    record CheckExecutionResult(Map<String, Integer> summary, List<CheckIssue> issues, Path annotatedFilePath) {
    }
}
