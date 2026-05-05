package com.formatcheck.backend.controller;

import com.formatcheck.backend.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileDownloadController {
    private final Path storageRoot;

    public FileDownloadController(AppProperties properties) {
        this.storageRoot = properties.getStorageRootPath().toAbsolutePath().normalize();
    }

    @GetMapping("/{*filePath}")
    public ResponseEntity<Resource> download(@PathVariable String filePath) throws Exception {
        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        Path resolved = storageRoot.resolve(cleanPath).normalize();
        if (!resolved.startsWith(storageRoot) || !Files.isRegularFile(resolved)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(resolved.toUri());
        String encodedName = URLEncoder.encode(resolved.getFileName().toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
