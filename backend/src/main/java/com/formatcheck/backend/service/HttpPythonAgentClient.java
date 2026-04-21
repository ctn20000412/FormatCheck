package com.formatcheck.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.domain.model.CheckIssue;
import com.formatcheck.backend.domain.model.RuleItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "http")
public class HttpPythonAgentClient implements AgentClient {

    private final RestClient restClient;

    public HttpPythonAgentClient(RestClient.Builder builder, AppProperties appProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(appProperties.getAgent().getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(appProperties.getAgent().getReadTimeoutSeconds()));

        this.restClient = builder
                .baseUrl(appProperties.getAgent().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ExtractionResult extractRules(String sessionId, Path standardFilePath, Path outputDir, String promptText, String schemaVersion, AgentExecutionOptions options) {
        RuleExtractAgentResponse response = post(
                "/internal/agent/rules/extract",
                new RuleExtractAgentRequest(
                        sessionId,
                        standardFilePath.toString(),
                        outputDir.toString(),
                        promptText,
                        schemaVersion,
                        options.provider().name(),
                        options.model(),
                        options.apiKey(),
                        options.apiBaseUrl(),
                        options.extraConfig()
                ),
                RuleExtractAgentResponse.class
        );
        if (response == null || response.rules() == null || response.rules().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.SERVICE_UNAVAILABLE, "Python Agent 未返回有效规则");
        }
        return new ExtractionResult(
                response.rules(),
                response.confidenceNote() == null ? "" : response.confidenceNote(),
                response.unidentifiedItems() == null ? List.of() : response.unidentifiedItems()
        );
    }

    @Override
    public CheckExecutionResult executeCheck(String sessionId, Path targetFilePath, Path ruleFilePath, Path outputDir, String promptText, String checkLevel, AgentExecutionOptions options) {
        CheckAgentResponse response = post(
                "/internal/agent/checks/execute",
                new CheckAgentRequest(
                        sessionId,
                        targetFilePath.toString(),
                        ruleFilePath.toString(),
                        outputDir.toString(),
                        promptText,
                        checkLevel,
                        options.provider().name(),
                        options.model(),
                        options.apiKey(),
                        options.apiBaseUrl(),
                        options.extraConfig()
                ),
                CheckAgentResponse.class
        );
        if (response == null || response.summary() == null || response.issues() == null || response.annotatedFilePath() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.SERVICE_UNAVAILABLE, "Python Agent 未返回有效检查结果");
        }
        return new CheckExecutionResult(
                response.summary(),
                response.issues(),
                Path.of(response.annotatedFilePath())
        );
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(URI.create(path))
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (ResourceAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, "Python Agent 服务不可达");
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.SERVICE_UNAVAILABLE, "Python Agent 调用失败: " + exception.getMessage());
        }
    }

    private record RuleExtractAgentRequest(
            String sessionId,
            String filePath,
            String outputDir,
            String promptText,
            String schemaVersion,
            String provider,
            String model,
            String apiKey,
            String apiBaseUrl,
            Map<String, String> extraConfig
    ) {
    }

    private record CheckAgentRequest(
            String sessionId,
            String targetFilePath,
            String ruleFilePath,
            String outputDir,
            String promptText,
            String checkLevel,
            String provider,
            String model,
            String apiKey,
            String apiBaseUrl,
            Map<String, String> extraConfig
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RuleExtractAgentResponse(
            List<RuleItem> rules,
            String confidenceNote,
            List<String> unidentifiedItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckAgentResponse(
            Map<String, Integer> summary,
            List<CheckIssue> issues,
            String annotatedFilePath
    ) {
    }
}
