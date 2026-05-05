package com.formatcheck.backend.service;

import java.util.Locale;
import java.util.Set;

public final class FileValidation {
    private static final Set<String> STANDARD_EXTENSIONS = Set.of("pdf", "doc", "docx", "md");

    private FileValidation() {}

    public static void requireStandardFile(String filename) {
        String extension = extensionOf(filename);
        if (!STANDARD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("格式规范文件仅支持 .pdf、.doc、.docx、.md");
        }
    }

    public static void requireCheckedFile(String filename) {
        String extension = extensionOf(filename);
        if (!"docx".equals(extension)) {
            throw new IllegalArgumentException("待检测论文当前仅支持 .docx");
        }
    }

    public static String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
