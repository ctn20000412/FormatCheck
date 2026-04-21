package com.formatcheck.backend.service;

import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.domain.enums.DownloadFileType;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;
import com.formatcheck.backend.domain.model.SessionMeta;
import com.formatcheck.backend.dto.response.ArtifactVO;
import com.formatcheck.backend.dto.response.FileUploadVO;
import com.formatcheck.backend.dto.response.SessionDetailVO;
import com.formatcheck.backend.dto.response.SessionVO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SessionService {

    private static final List<String> SUPPORTED_DOC_EXTENSIONS = List.of(".docx", ".pdf", ".md", ".txt");

    private final StorageService storageService;

    public SessionService(StorageService storageService) {
        this.storageService = storageService;
    }

    public SessionVO createSession(SessionType type, String name) {
        SessionMeta meta = storageService.createSession(type, name);
        return new SessionVO(meta.getSessionId(), meta.getType(), meta.getStatus(), meta.getCreatedAt());
    }

    public FileUploadVO uploadStandardFile(String sessionId, MultipartFile file) {
        SessionMeta meta = requireSession(sessionId);
        if (meta.getType() != SessionType.RULE_EXTRACTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持上传规范文档");
        }
        validateFileExtension(file.getOriginalFilename(), SUPPORTED_DOC_EXTENSIONS, "文件类型不支持");
        Path saved = storageService.saveSourceFile(sessionId, file, true);
        meta.setStandardFileName(saved.getFileName().toString());
        meta.setStatus(TaskStatus.UPLOADED);
        storageService.saveMeta(meta);
        return new FileUploadVO(sessionId, saved.getFileName().toString(), meta.getStatus());
    }

    public FileUploadVO uploadTargetFile(String sessionId, MultipartFile file) {
        SessionMeta meta = requireSession(sessionId);
        if (meta.getType() != SessionType.FORMAT_CHECK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "当前会话不支持上传待检查文档");
        }
        validateFileExtension(file.getOriginalFilename(), SUPPORTED_DOC_EXTENSIONS, "文件类型不支持");
        Path saved = storageService.saveSourceFile(sessionId, file, false);
        meta.setTargetFileName(saved.getFileName().toString());
        meta.setStatus(TaskStatus.UPLOADED);
        storageService.saveMeta(meta);
        return new FileUploadVO(sessionId, saved.getFileName().toString(), meta.getStatus());
    }

    public SessionDetailVO getSessionDetail(String sessionId) {
        SessionMeta meta = requireSession(sessionId);
        return new SessionDetailVO(
                meta.getSessionId(),
                meta.getName(),
                meta.getType(),
                meta.getStatus(),
                meta.getCreatedAt(),
                meta.getUpdatedAt(),
                meta.getRuleVersion(),
                meta.getStandardFileName(),
                meta.getTargetFileName(),
                meta.getRuleSource(),
                meta.getErrorMessage(),
                meta.getAgentProvider(),
                meta.getAgentModel(),
                buildArtifacts(meta.getSessionId())
        );
    }

    public Resource download(String sessionId, DownloadFileType fileType) {
        return storageService.loadAsResource(storageService.resolveDownloadPath(sessionId, fileType));
    }

    public String resolveDownloadFileName(String sessionId, DownloadFileType fileType) {
        return storageService.resolveDownloadPath(sessionId, fileType).getFileName().toString();
    }

    public SessionMeta requireSession(String sessionId) {
        return storageService.readMeta(sessionId);
    }

    private List<ArtifactVO> buildArtifacts(String sessionId) {
        List<ArtifactVO> artifacts = new ArrayList<>();
        for (Path path : storageService.listArtifacts(sessionId)) {
            DownloadFileType fileType = detectFileType(sessionId, path);
            artifacts.add(new ArtifactVO(
                    path.getFileName().toString(),
                    fileType.name(),
                    "/api/files/download?sessionId=" + sessionId + "&fileType=" + fileType.name()
            ));
        }
        return artifacts;
    }

    private DownloadFileType detectFileType(String sessionId, Path path) {
        String fileName = path.getFileName().toString();
        SessionMeta meta = requireSession(sessionId);
        if (fileName.equals(meta.getStandardFileName())) {
            return DownloadFileType.STANDARD_DOC;
        }
        if (fileName.equals(meta.getTargetFileName())) {
            return DownloadFileType.TARGET_DOC;
        }
        if ("规范.js".equals(fileName)) {
            return DownloadFileType.RULE_DRAFT;
        }
        if ("完整规范.js".equals(fileName)) {
            return DownloadFileType.RULE_FINAL;
        }
        if ("report.json".equals(fileName)) {
            return DownloadFileType.REPORT_JSON;
        }
        if ("report.md".equals(fileName)) {
            return DownloadFileType.REPORT_MD;
        }
        if (fileName.contains(".annotated.")) {
            return DownloadFileType.ANNOTATED_DOC;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "未知产物类型");
    }

    private void validateFileExtension(String fileName, List<String> supportedExtensions, String errorMessage) {
        if (!StringUtils.hasText(fileName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        boolean matched = supportedExtensions.stream().anyMatch(lower::endsWith);
        if (!matched) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, errorMessage);
        }
    }
}
