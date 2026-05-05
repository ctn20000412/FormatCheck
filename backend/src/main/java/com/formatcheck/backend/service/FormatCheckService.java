package com.formatcheck.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.dto.CheckTaskResponse;
import com.formatcheck.backend.dto.FileLink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FormatCheckService {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final ModelCatalogService modelCatalogService;
    private final PythonAgentClient pythonAgentClient;

    public FormatCheckService(
            AppProperties properties,
            ObjectMapper objectMapper,
            ModelCatalogService modelCatalogService,
            PythonAgentClient pythonAgentClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.modelCatalogService = modelCatalogService;
        this.pythonAgentClient = pythonAgentClient;
    }

    public CheckTaskResponse runCheck(
            String provider,
            String model,
            String workflow,
            MultipartFile standardFile,
            MultipartFile checkedFile,
            String resultFolderName)
            throws Exception {
        validateModel(provider, model);
        String normalizedWorkflow = normalizeWorkflow(workflow);
        String standardFilename = requireFilename(standardFile);
        String checkedFilename = requireFilename(checkedFile);
        FileValidation.requireStandardFile(standardFilename);
        FileValidation.requireCheckedFile(checkedFilename);

        ResultWorkspace workspace =
                new ResultWorkspaceFactory(properties.getStorageRootPath()).create(checkedFilename, resultFolderName);
        Path savedStandard = workspace.standardFileDir().resolve(standardFilename);
        Path savedChecked = workspace.checkedFileDir().resolve(checkedFilename);
        standardFile.transferTo(savedStandard);
        checkedFile.transferTo(savedChecked);

        Path annotatedOutput = workspace.annotatedOutput(checkedFilename);
        Path requestJson = workspace.checkResultDir().resolve("agent_request.json");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", provider);
        request.put("model", model);
        request.put("workflow", normalizedWorkflow);
        request.put("work_dir", workspace.root().toString());
        request.put("checked_file", savedChecked.toString());
        request.put("standard_file", savedStandard.toString());
        request.put("format_rule_output", workspace.formatRuleOutput().toString());
        request.put("check_result_output", workspace.checkResultOutput().toString());
        request.put("analysis_output", workspace.analysisOutput().toString());
        request.put("annotated_output", annotatedOutput.toString());
        request.put("llm_config_path", properties.getModelConfigPath().toString());
        request.put("llm_local_config_path", properties.getLocalModelConfigPath().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(requestJson.toFile(), request);

        AgentProcessResult result = pythonAgentClient.run(request);
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }

        return new CheckTaskResponse(
                true,
                result.message(),
                normalizedWorkflow,
                workspace.folderName(),
                link("下载格式规范 JSON", "format_rule", workspace.formatRuleOutput()),
                link("下载错误统计分析文档", "analysis_doc", workspace.analysisOutput()),
                link("下载批注版论文", "annotated_paper", annotatedOutput),
                result.statistics(),
                asMap(result.payload().get("compare_result")),
                result.issues());
    }

    private void validateModel(String provider, String model) {
        try {
            modelCatalogService.requireSupported(provider, model);
        } catch (IOException e) {
            throw new IllegalStateException("读取模型配置失败", e);
        }
    }

    private String normalizeWorkflow(String workflow) {
        if (workflow == null || workflow.isBlank()) {
            return "full_check";
        }
        return switch (workflow) {
            case "base_format", "language_semantic", "full_check" -> workflow;
            case "hard_format" -> "base_format";
            case "semantic_llm", "llm_direct" -> "language_semantic";
            case "hybrid", "compare" -> "full_check";
            default -> throw new IllegalArgumentException("Unsupported workflow: " + workflow);
        };
    }

    private String requireFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = Path.of(file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
                .getFileName()
                .toString();
        if (filename.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        return filename;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private FileLink link(String label, String fileType, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalStateException("结果文件不存在: " + normalized);
        }
        Path root = properties.getStorageRootPath().toAbsolutePath().normalize();
        String relative = root.relativize(normalized).toString().replace('\\', '/');
        return new FileLink(label, fileType, normalized.toString(), "/api/files/" + relative);
    }
}
