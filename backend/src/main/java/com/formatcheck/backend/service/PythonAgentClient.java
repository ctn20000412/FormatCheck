package com.formatcheck.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PythonAgentClient {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PythonAgentClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getAgentTimeoutSeconds()))
                .build();
    }

    public AgentProcessResult run(Map<String, Object> requestPayload) throws IOException, InterruptedException {
        String requestJson = objectMapper.writeValueAsString(requestPayload);
        HttpRequest request = HttpRequest.newBuilder(agentUri("/agent/check"))
                .timeout(Duration.ofSeconds(properties.getAgentTimeoutSeconds()))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Python Agent HTTP 调用失败，状态码 "
                    + response.statusCode() + ": " + response.body());
        }

        Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
        return new AgentProcessResult(
                Boolean.TRUE.equals(payload.get("success")),
                String.valueOf(payload.getOrDefault("message", "")),
                asMap(payload.get("statistics")),
                asList(payload.get("issues")),
                payload);
    }

    private URI agentUri(String path) {
        String baseUrl = properties.getAgentBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
