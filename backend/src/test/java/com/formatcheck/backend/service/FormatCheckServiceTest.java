package com.formatcheck.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.dto.CheckTaskResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FormatCheckServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesUploadsCallsLongRunningPythonAgentAndReturnsDownloadLinks() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<Map<String, Object>> capturedRequest = new AtomicReference<>();
        HttpServer agentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        agentServer.createContext("/agent/check", exchange -> {
            Map<String, Object> request =
                    objectMapper.readValue(exchange.getRequestBody(), Map.class);
            capturedRequest.set(request);
            for (String key : new String[] {"format_rule_output", "check_result_output", "analysis_output", "annotated_output"}) {
                Path.of(String.valueOf(request.get(key))).getParent().toFile().mkdirs();
            }
            Files.writeString(Path.of(String.valueOf(request.get("format_rule_output"))), "{\"rules\":[]}");
            Files.writeString(
                    Path.of(String.valueOf(request.get("check_result_output"))),
                    "{\"statistics\":{\"total_issues\":0},\"issues\":[]}");
            Files.write(Path.of(String.valueOf(request.get("analysis_output"))), "fake docx".getBytes());
            Files.copy(
                    Path.of(String.valueOf(request.get("checked_file"))),
                    Path.of(String.valueOf(request.get("annotated_output"))));
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "success", true,
                    "message", "completed",
                    "compare_result", Map.of(
                            "llm_direct_total_issues", 1,
                            "hybrid_total_issues", 2,
                            "deterministic_added_issues", 1,
                            "recommended_workflow", "full_check"),
                    "statistics", Map.of("total_issues", 0),
                    "issues", java.util.List.of()));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        agentServer.start();

        AppProperties properties = new AppProperties();
        properties.setStorageRoot(tempDir.resolve("results").toString());
        properties.setAgentBaseUrl("http://127.0.0.1:" + agentServer.getAddress().getPort());
        Path modelConfig = tempDir.resolve("llm_providers.json");
        Files.writeString(
                modelConfig,
                """
                {
                  "providers": [
                    {
                      "id": "deepseek",
                      "models": [{"id": "deepseek-chat"}]
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        properties.setModelConfig(modelConfig.toString());
        properties.setAgentTimeoutSeconds(30);

        ModelCatalogService modelCatalogService = new ModelCatalogService(properties, objectMapper);
        PythonAgentClient pythonAgentClient = new PythonAgentClient(properties, objectMapper);
        FormatCheckService service = new FormatCheckService(properties, objectMapper, modelCatalogService, pythonAgentClient);
        MockMultipartFile standardFile =
                new MockMultipartFile("standardFile", "standard.pdf", "application/pdf", "fake pdf".getBytes());
        MockMultipartFile checkedFile = new MockMultipartFile(
                "checkedFile",
                "paper.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake docx".getBytes());

        CheckTaskResponse response;
        try {
            response = service.runCheck("deepseek", "deepseek-chat", "full_check", standardFile, checkedFile, null);
        } finally {
            agentServer.stop(0);
        }

        assertEquals("full_check", capturedRequest.get().get("workflow"));
        assertEquals("full_check", response.compareResult().get("recommended_workflow"));
        Path workspace = tempDir.resolve("results").resolve("paper");
        assertTrue(Files.exists(workspace.resolve("待检测文件").resolve("paper.docx")));
        assertTrue(Files.exists(workspace.resolve("规则规范文件").resolve("standard.pdf")));
        assertTrue(Files.exists(workspace.resolve("规则抽取结果").resolve("format_rule.json")));
        assertTrue(Files.exists(workspace.resolve("检测结果").resolve("format_check_analysis.docx")));
        assertTrue(Files.exists(workspace.resolve("检测后带批注的源文件").resolve("paper_格式检查批注版.docx")));
        assertEquals(0, response.statistics().get("total_issues"));
        assertTrue(response.formatRule().url().contains("/api/files/paper/"));
        assertTrue(response.analysisDoc().url().contains("/api/files/paper/"));
        assertTrue(response.annotatedDoc().url().contains("/api/files/paper/"));
    }
}
