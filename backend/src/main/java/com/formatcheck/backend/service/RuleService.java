package com.formatcheck.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.domain.enums.RuleSource;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.RuleItem;
import com.formatcheck.backend.domain.model.SessionMeta;
import com.formatcheck.backend.dto.response.RuleConfirmVO;
import com.formatcheck.backend.dto.response.RuleImportVO;
import com.formatcheck.backend.dto.response.RuleVO;
import com.formatcheck.backend.dto.response.TaskVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleService {

    private final StorageService storageService;
    private final SessionService sessionService;
    private final PromptTemplateService promptTemplateService;
    private final AgentProviderService agentProviderService;
    private final AgentClient agentClient;
    private final RuleFileCodec ruleFileCodec;
    private final ObjectMapper objectMapper;

    public RuleService(StorageService storageService,
                       SessionService sessionService,
                       PromptTemplateService promptTemplateService,
                       AgentProviderService agentProviderService,
                       AgentClient agentClient,
                       RuleFileCodec ruleFileCodec,
                       ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.sessionService = sessionService;
        this.promptTemplateService = promptTemplateService;
        this.agentProviderService = agentProviderService;
        this.agentClient = agentClient;
        this.ruleFileCodec = ruleFileCodec;
        this.objectMapper = objectMapper;
    }

    public TaskVO extractRules(String sessionId, String schemaVersion, com.formatcheck.backend.domain.enums.AgentProvider provider, String model) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        if (meta.getType() != SessionType.RULE_EXTRACTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持规则抽取");
        }
        if (!StringUtils.hasText(meta.getStandardFileName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "缺少规范文件");
        }

        meta.setStatus(TaskStatus.PROCESSING);
        AgentExecutionOptions options = agentProviderService.resolve(provider, model);
        meta.setAgentProvider(options.provider());
        meta.setAgentModel(options.model());
        storageService.saveMeta(meta);
        Path standardFile = storageService.getSourceDirectory(sessionId).resolve(meta.getStandardFileName());
        String promptText = promptTemplateService.getRuleExtractPrompt();
        storageService.writeLog(sessionId, "agent-request.json", Map.of(
                "sessionId", sessionId,
                "filePath", standardFile.toString(),
                "outputDir", storageService.getRulesDirectory(sessionId).toString(),
                "promptText", promptText,
                "schemaVersion", schemaVersion == null ? "1.0" : schemaVersion,
                "provider", options.provider(),
                "model", options.model()
        ));

        try {
            AgentClient.ExtractionResult result = agentClient.extractRules(
                    sessionId,
                    standardFile,
                    storageService.getRulesDirectory(sessionId),
                    promptText,
                    schemaVersion == null ? "1.0" : schemaVersion,
                    options
            );
            ruleFileCodec.writeJsonRules(storageService.getDraftRuleJsonPath(sessionId), result.rules());
            ruleFileCodec.writeModuleRules(storageService.getDraftRuleModulePath(sessionId), result.rules());
            storageService.writeLog(sessionId, "agent-response.json", result);
            meta.setRuleSource(RuleSource.EXTRACTED);
            meta.setStatus(TaskStatus.WAITING_CONFIRM);
            meta.setErrorMessage(null);
            storageService.saveMeta(meta);
            return new TaskVO(sessionId, meta.getStatus(), "规则抽取完成");
        } catch (RuntimeException exception) {
            meta.setStatus(TaskStatus.FAILED);
            meta.setErrorMessage(exception.getMessage());
            storageService.saveMeta(meta);
            throw exception;
        }
    }

    public RuleVO getRules(String sessionId) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        boolean confirmed = Files.exists(storageService.getFinalRuleJsonPath(sessionId));
        Path rulePath = confirmed ? storageService.getFinalRuleJsonPath(sessionId) : storageService.getDraftRuleJsonPath(sessionId);
        if (!Files.exists(rulePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "规则文件不存在");
        }
        List<RuleItem> rules = ruleFileCodec.readJsonRules(rulePath);
        return new RuleVO(sessionId, meta.getStatus(), confirmed, rules);
    }

    public RuleConfirmVO confirmRules(String sessionId, List<RuleItem> rules, String versionNote) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        if (meta.getType() != SessionType.RULE_EXTRACTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持规则确认");
        }
        if (meta.getStatus() != TaskStatus.WAITING_CONFIRM && meta.getStatus() != TaskStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前状态不允许确认规则");
        }
        ruleFileCodec.writeJsonRules(storageService.getFinalRuleJsonPath(sessionId), rules);
        ruleFileCodec.writeModuleRules(storageService.getFinalRuleModulePath(sessionId), rules);
        storageService.deleteIfExists(storageService.getDraftRuleJsonPath(sessionId));
        storageService.deleteIfExists(storageService.getDraftRuleModulePath(sessionId));
        storageService.writeLog(sessionId, "rule-confirm.json", Map.of(
                "versionNote", versionNote == null ? "" : versionNote,
                "ruleCount", rules.size()
        ));

        meta.setStatus(TaskStatus.CONFIRMED);
        meta.setRuleVersion(meta.getRuleVersion() == null ? 1 : meta.getRuleVersion() + 1);
        meta.setRuleSource(RuleSource.EXTRACTED);
        storageService.saveMeta(meta);
        return new RuleConfirmVO(sessionId, "完整规范.js", meta.getRuleVersion(), meta.getStatus());
    }

    public RuleImportVO importFinalRules(String sessionId, MultipartFile file) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        if (meta.getType() != SessionType.RULE_IMPORT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持规则直传");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".js")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则文件必须为 .js");
        }

        try {
            String rawContent = new String(file.getBytes());
            List<RuleItem> rules = ruleFileCodec.parseRuleModule(rawContent);
            validateRules(rules);
            storageService.saveTextFile(storageService.getFinalRuleModulePath(sessionId), rawContent);
            ruleFileCodec.writeJsonRules(storageService.getFinalRuleJsonPath(sessionId), rules);

            meta.setRuleSource(RuleSource.IMPORTED);
            meta.setStatus(TaskStatus.CONFIRMED);
            meta.setRuleVersion(1);
            meta.setErrorMessage(null);
            storageService.saveMeta(meta);
            return new RuleImportVO(sessionId, "完整规范.js", true, meta.getStatus());
        } catch (ApiException exception) {
            meta.setStatus(TaskStatus.FAILED);
            meta.setErrorMessage(exception.getMessage());
            storageService.saveMeta(meta);
            throw exception;
        } catch (Exception exception) {
            meta.setStatus(TaskStatus.FAILED);
            meta.setErrorMessage(exception.getMessage());
            storageService.saveMeta(meta);
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则结构校验失败");
        }
    }

    public List<RuleItem> loadConfirmedRules(String sessionId) {
        SessionMeta meta = sessionService.requireSession(sessionId);
        if (meta.getStatus() != TaskStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则未确认");
        }
        Path rulePath = storageService.getFinalRuleJsonPath(sessionId);
        if (!Files.exists(rulePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "完整规则文件不存在");
        }
        return ruleFileCodec.readJsonRules(rulePath);
    }

    private void validateRules(List<RuleItem> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则内容不能为空");
        }
        for (RuleItem rule : rules) {
            Map<String, Object> asMap = objectMapper.convertValue(rule, LinkedHashMap.class);
            List<String> requiredFields = List.of(
                    "rule_id", "category", "scope", "rule_name", "description",
                    "check_type", "check_object", "expected", "severity", "source_text", "applicable_sections"
            );
            for (String field : requiredFields) {
                Object value = asMap.get(field);
                if (value == null || (value instanceof String string && string.isBlank())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则结构校验失败");
                }
            }
        }
    }
}
