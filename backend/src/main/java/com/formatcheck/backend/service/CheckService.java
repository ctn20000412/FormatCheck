package com.formatcheck.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.domain.enums.DownloadFileType;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.CheckIssue;
import com.formatcheck.backend.domain.model.SessionMeta;
import com.formatcheck.backend.dto.response.ArtifactVO;
import com.formatcheck.backend.dto.response.CheckResultVO;
import com.formatcheck.backend.dto.response.TaskVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class CheckService {

    private final StorageService storageService;
    private final SessionService sessionService;
    private final RuleService ruleService;
    private final PromptTemplateService promptTemplateService;
    private final AgentProviderService agentProviderService;
    private final AgentClient agentClient;
    private final ObjectMapper objectMapper;

    public CheckService(StorageService storageService,
                        SessionService sessionService,
                        RuleService ruleService,
                        PromptTemplateService promptTemplateService,
                        AgentProviderService agentProviderService,
                        AgentClient agentClient,
                        ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.sessionService = sessionService;
        this.ruleService = ruleService;
        this.promptTemplateService = promptTemplateService;
        this.agentProviderService = agentProviderService;
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
    }

    public TaskVO executeCheck(String sessionId, String ruleSessionId, String checkLevel, com.formatcheck.backend.domain.enums.AgentProvider provider, String model) {
        SessionMeta checkMeta = sessionService.requireSession(sessionId);
        if (checkMeta.getType() != SessionType.FORMAT_CHECK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持格式检查");
        }
        if (!StringUtils.hasText(checkMeta.getTargetFileName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "缺少待检查文档");
        }

        ruleService.loadConfirmedRules(ruleSessionId);
        checkMeta.setStatus(TaskStatus.PROCESSING);
        AgentExecutionOptions options = agentProviderService.resolve(provider, model);
        checkMeta.setAgentProvider(options.provider());
        checkMeta.setAgentModel(options.model());
        storageService.saveMeta(checkMeta);

        Path targetFilePath = storageService.getSourceDirectory(sessionId).resolve(checkMeta.getTargetFileName());
        Path ruleFilePath = storageService.getFinalRuleModulePath(ruleSessionId);
        String promptText = promptTemplateService.getFormatCheckPrompt();
        storageService.writeLog(sessionId, "agent-request.json", Map.of(
                "sessionId", sessionId,
                "targetFilePath", targetFilePath.toString(),
                "ruleFilePath", ruleFilePath.toString(),
                "outputDir", storageService.getOutputDirectory(sessionId).toString(),
                "promptText", promptText,
                "checkLevel", checkLevel == null ? "NORMAL" : checkLevel,
                "provider", options.provider(),
                "model", options.model()
        ));

        try {
            AgentClient.CheckExecutionResult result = agentClient.executeCheck(
                    sessionId,
                    targetFilePath,
                    ruleFilePath,
                    storageService.getOutputDirectory(sessionId),
                    promptText,
                    checkLevel == null ? "NORMAL" : checkLevel,
                    options
            );
            writeReport(sessionId, result.summary(), result.issues());
            if (!Files.exists(result.annotatedFilePath())) {
                storageService.copyFile(targetFilePath, result.annotatedFilePath());
            }
            storageService.writeLog(sessionId, "agent-response.json", result);
            checkMeta.setStatus(TaskStatus.COMPLETED);
            checkMeta.setErrorMessage(null);
            storageService.saveMeta(checkMeta);
            return new TaskVO(sessionId, checkMeta.getStatus(), "格式检查完成");
        } catch (RuntimeException exception) {
            checkMeta.setStatus(TaskStatus.FAILED);
            checkMeta.setErrorMessage(exception.getMessage());
            storageService.saveMeta(checkMeta);
            throw exception;
        }
    }

    public CheckResultVO getResult(String sessionId) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        if (meta.getType() != SessionType.FORMAT_CHECK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不是检查会话");
        }
        if (meta.getStatus() != TaskStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "检查尚未完成");
        }
        List<ArtifactVO> reportFiles = List.of(
                new ArtifactVO("report.md", "REPORT_MD", "/api/files/download?sessionId=" + sessionId + "&fileType=REPORT_MD"),
                new ArtifactVO("report.json", "REPORT_JSON", "/api/files/download?sessionId=" + sessionId + "&fileType=REPORT_JSON"),
                new ArtifactVO(
                        storageService.resolveDownloadPath(sessionId, DownloadFileType.ANNOTATED_DOC).getFileName().toString(),
                        "ANNOTATED_DOC",
                        "/api/files/download?sessionId=" + sessionId + "&fileType=ANNOTATED_DOC"
                )
        );
        return new CheckResultVO(
                sessionId,
                meta.getStatus(),
                storageService.readReportSummary(sessionId),
                storageService.readIssues(sessionId),
                reportFiles
        );
    }

    private void writeReport(String sessionId, Map<String, Integer> summary, List<CheckIssue> issues) {
        Map<String, Object> report = Map.of(
                "sessionId", sessionId,
                "summary", summary,
                "issues", issues
        );
        try {
            Files.writeString(storageService.getReportJsonPath(sessionId),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            Files.writeString(storageService.getReportMarkdownPath(sessionId), buildMarkdown(summary, issues));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "生成检查报告失败");
        }
    }

    private String buildMarkdown(Map<String, Integer> summary, List<CheckIssue> issues) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 检查报告").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- 问题总数：").append(summary.getOrDefault("totalIssues", 0)).append(System.lineSeparator());
        builder.append("- 严重问题：").append(summary.getOrDefault("criticalIssues", 0)).append(System.lineSeparator());
        builder.append("- 主要问题：").append(summary.getOrDefault("majorIssues", 0)).append(System.lineSeparator());
        builder.append("- 次要问题：").append(summary.getOrDefault("minorIssues", 0)).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("## 问题列表").append(System.lineSeparator()).append(System.lineSeparator());
        for (CheckIssue issue : issues) {
            builder.append("### ").append(issue.id()).append(" - ").append(issue.ruleName()).append(System.lineSeparator());
            builder.append("- 位置：第").append(issue.location().page()).append("页 / 段落 ").append(issue.location().paragraphIndex())
                    .append(" / ").append(issue.location().heading()).append(System.lineSeparator());
            builder.append("- 严重程度：").append(issue.severity()).append(System.lineSeparator());
            builder.append("- 原因：").append(issue.reason()).append(System.lineSeparator());
            builder.append("- 建议：").append(issue.suggestion()).append(System.lineSeparator()).append(System.lineSeparator());
        }
        return builder.toString();
    }
}
