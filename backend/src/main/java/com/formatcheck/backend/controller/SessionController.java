package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.domain.enums.DownloadFileType;
import com.formatcheck.backend.dto.request.CreateSessionRequest;
import com.formatcheck.backend.dto.response.FileUploadVO;
import com.formatcheck.backend.dto.response.SessionDetailVO;
import com.formatcheck.backend.dto.response.SessionVO;
import com.formatcheck.backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionVO> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionService.createSession(request.type(), request.name()));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionDetailVO> getSession(@PathVariable String sessionId) {
        return ApiResponse.success(sessionService.getSessionDetail(sessionId));
    }

    @PostMapping(value = "/sessions/{sessionId}/files/standard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadVO> uploadStandard(@PathVariable String sessionId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(sessionService.uploadStandardFile(sessionId, file));
    }

    @PostMapping(value = "/sessions/{sessionId}/files/target", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadVO> uploadTarget(@PathVariable String sessionId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(sessionService.uploadTargetFile(sessionId, file));
    }

    @GetMapping("/files/download")
    public ResponseEntity<Resource> download(@RequestParam String sessionId, @RequestParam DownloadFileType fileType) {
        Resource resource = sessionService.download(sessionId, fileType);
        String fileName = sessionService.resolveDownloadFileName(sessionId, fileType);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
