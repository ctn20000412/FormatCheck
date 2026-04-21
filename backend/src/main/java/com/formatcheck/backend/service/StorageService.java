package com.formatcheck.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.domain.enums.DownloadFileType;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.CheckIssue;
import com.formatcheck.backend.domain.model.SessionMeta;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {

    private static final TypeReference<Map<String, Integer>> SUMMARY_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<CheckIssue>> ISSUE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path rootDirectory;

    public StorageService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.rootDirectory = Path.of(appProperties.getStorage().getRoot()).toAbsolutePath().normalize();
        createDirectories(rootDirectory);
    }

    public SessionMeta createSession(SessionType type, String name) {
        String sessionId = "sess_" + OffsetDateTime.now(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "_" + UUID.randomUUID().toString().substring(0, 6);
        SessionMeta meta = new SessionMeta();
        meta.setSessionId(sessionId);
        meta.setName(name);
        meta.setType(type);
        meta.setStatus(TaskStatus.CREATED);
        meta.setCreatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        meta.setUpdatedAt(meta.getCreatedAt());
        meta.setRuleVersion(0);
        meta.setErrorMessage(null);

        createDirectories(getSessionDirectory(sessionId));
        createDirectories(getSourceDirectory(sessionId));
        createDirectories(getRulesDirectory(sessionId));
        createDirectories(getOutputDirectory(sessionId));
        createDirectories(getLogsDirectory(sessionId));
        saveMeta(meta);
        return meta;
    }

    public SessionMeta readMeta(String sessionId) {
        Path metaPath = getSessionDirectory(sessionId).resolve("meta.json");
        if (!Files.exists(metaPath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "会话不存在");
        }
        try {
            return objectMapper.readValue(Files.readString(metaPath), SessionMeta.class);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "读取会话元数据失败");
        }
    }

    public void saveMeta(SessionMeta meta) {
        meta.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        try {
            Files.writeString(getSessionDirectory(meta.getSessionId()).resolve("meta.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "保存会话元数据失败");
        }
    }

    public Path saveSourceFile(String sessionId, MultipartFile file, boolean standardFile) {
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        Path target = getSourceDirectory(sessionId).resolve(originalFileName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "保存上传文件失败");
        }
    }

    public Path saveTextFile(Path path, String content) {
        createDirectories(path.getParent());
        try {
            return Files.writeString(path, content);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "保存文件失败");
        }
    }

    public void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "删除文件失败");
        }
    }

    public void copyFile(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "复制文件失败");
        }
    }

    public void writeLog(String sessionId, String fileName, Object data) {
        Path logPath = getLogsDirectory(sessionId).resolve(fileName);
        try {
            Files.writeString(logPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "写入日志失败");
        }
    }

    public Path getSessionDirectory(String sessionId) {
        return rootDirectory.resolve(sessionId);
    }

    public Path getSourceDirectory(String sessionId) {
        return getSessionDirectory(sessionId).resolve("source");
    }

    public Path getRulesDirectory(String sessionId) {
        return getSessionDirectory(sessionId).resolve("rules");
    }

    public Path getOutputDirectory(String sessionId) {
        return getSessionDirectory(sessionId).resolve("output");
    }

    public Path getLogsDirectory(String sessionId) {
        return getSessionDirectory(sessionId).resolve("logs");
    }

    public Path getDraftRuleJsonPath(String sessionId) {
        return getRulesDirectory(sessionId).resolve("rules.json");
    }

    public Path getDraftRuleModulePath(String sessionId) {
        return getRulesDirectory(sessionId).resolve("规范.js");
    }

    public Path getFinalRuleJsonPath(String sessionId) {
        return getRulesDirectory(sessionId).resolve("final-rules.json");
    }

    public Path getFinalRuleModulePath(String sessionId) {
        return getRulesDirectory(sessionId).resolve("完整规范.js");
    }

    public Path getReportJsonPath(String sessionId) {
        return getOutputDirectory(sessionId).resolve("report.json");
    }

    public Path getReportMarkdownPath(String sessionId) {
        return getOutputDirectory(sessionId).resolve("report.md");
    }

    public Path resolveDownloadPath(String sessionId, DownloadFileType fileType) {
        SessionMeta meta = readMeta(sessionId);
        return switch (fileType) {
            case STANDARD_DOC -> {
                if (meta.getStandardFileName() == null) {
                    throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "规范文档不存在");
                }
                yield getSourceDirectory(sessionId).resolve(meta.getStandardFileName());
            }
            case TARGET_DOC -> {
                if (meta.getTargetFileName() == null) {
                    throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "待检文档不存在");
                }
                yield getSourceDirectory(sessionId).resolve(meta.getTargetFileName());
            }
            case RULE_DRAFT -> getDraftRuleModulePath(sessionId);
            case RULE_FINAL -> getFinalRuleModulePath(sessionId);
            case REPORT_JSON -> getReportJsonPath(sessionId);
            case REPORT_MD -> getReportMarkdownPath(sessionId);
            case ANNOTATED_DOC -> resolveAnnotatedDocument(sessionId);
        };
    }

    public Resource loadAsResource(Path path) {
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "文件不存在");
        }
        return new FileSystemResource(path);
    }

    public List<Path> listArtifacts(String sessionId) {
        List<Path> paths = new ArrayList<>();
        addIfExists(paths, getDraftRuleModulePath(sessionId));
        addIfExists(paths, getFinalRuleModulePath(sessionId));
        addIfExists(paths, getReportJsonPath(sessionId));
        addIfExists(paths, getReportMarkdownPath(sessionId));
        Path annotated = resolveAnnotatedDocumentIfPresent(sessionId);
        if (annotated != null) {
            paths.add(annotated);
        }
        SessionMeta meta = readMeta(sessionId);
        if (meta.getStandardFileName() != null) {
            addIfExists(paths, getSourceDirectory(sessionId).resolve(meta.getStandardFileName()));
        }
        if (meta.getTargetFileName() != null) {
            addIfExists(paths, getSourceDirectory(sessionId).resolve(meta.getTargetFileName()));
        }
        return paths;
    }

    public Map<String, Integer> readReportSummary(String sessionId) {
        try {
            var root = objectMapper.readTree(Files.readString(getReportJsonPath(sessionId)));
            if (root == null || root.get("summary") == null || root.get("summary").isNull()) {
                return Map.of();
            }
            return objectMapper.convertValue(root.get("summary"), SUMMARY_TYPE);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "读取报告摘要失败");
        }
    }

    public List<CheckIssue> readIssues(String sessionId) {
        try {
            var root = objectMapper.readTree(Files.readString(getReportJsonPath(sessionId)));
            if (root == null || root.get("issues") == null || root.get("issues").isNull()) {
                return List.of();
            }
            return objectMapper.convertValue(root.get("issues"), ISSUE_LIST_TYPE);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "读取检查结果失败");
        }
    }

    private Path resolveAnnotatedDocument(String sessionId) {
        Path annotated = resolveAnnotatedDocumentIfPresent(sessionId);
        if (annotated == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "批注文档不存在");
        }
        return annotated;
    }

    private Path resolveAnnotatedDocumentIfPresent(String sessionId) {
        try {
            return Files.list(getOutputDirectory(sessionId))
                    .filter(path -> path.getFileName().toString().contains(".annotated."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "读取输出目录失败");
        }
    }

    private void addIfExists(List<Path> paths, Path path) {
        if (Files.exists(path)) {
            paths.add(path);
        }
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "创建目录失败");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        String normalized = Path.of(fileName).getFileName().toString();
        if (normalized.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "非法文件名");
        }
        return normalized;
    }
}
