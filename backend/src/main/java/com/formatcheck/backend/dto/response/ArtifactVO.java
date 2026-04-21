package com.formatcheck.backend.dto.response;

public record ArtifactVO(
        String fileName,
        String fileType,
        String downloadUrl
) {
}
