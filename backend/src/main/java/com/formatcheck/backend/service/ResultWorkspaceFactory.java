package com.formatcheck.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResultWorkspaceFactory {
    public static final String CHECKED_FILE_DIR = "待检测文件";
    public static final String STANDARD_FILE_DIR = "规则规范文件";
    public static final String RULE_EXTRACTION_DIR = "规则抽取结果";
    public static final String CHECK_RESULT_DIR = "检测结果";
    public static final String ANNOTATED_SOURCE_DIR = "检测后带批注的源文件";

    private final Path storageRoot;

    public ResultWorkspaceFactory(Path storageRoot) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    public ResultWorkspace create(String checkedFilename, String explicitFolderName) throws IOException {
        String folderName = sanitizeFolderName(
                explicitFolderName == null || explicitFolderName.isBlank()
                        ? stripExtension(checkedFilename)
                        : explicitFolderName);
        Path root = storageRoot.resolve(folderName).normalize();
        if (!root.startsWith(storageRoot)) {
            throw new IllegalArgumentException("结果目录非法");
        }

        Path checkedFileDir = root.resolve(CHECKED_FILE_DIR);
        Path standardFileDir = root.resolve(STANDARD_FILE_DIR);
        Path ruleExtractionDir = root.resolve(RULE_EXTRACTION_DIR);
        Path checkResultDir = root.resolve(CHECK_RESULT_DIR);
        Path annotatedSourceDir = root.resolve(ANNOTATED_SOURCE_DIR);
        Files.createDirectories(checkedFileDir);
        Files.createDirectories(standardFileDir);
        Files.createDirectories(ruleExtractionDir);
        Files.createDirectories(checkResultDir);
        Files.createDirectories(annotatedSourceDir);
        return new ResultWorkspace(
                folderName,
                root,
                checkedFileDir,
                standardFileDir,
                ruleExtractionDir,
                checkResultDir,
                annotatedSourceDir);
    }

    public static String sanitizeFolderName(String value) {
        String sanitized = value == null ? "" : value.trim();
        sanitized = sanitized.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "document" : sanitized;
    }

    private static String stripExtension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : String.valueOf(filename);
    }
}
