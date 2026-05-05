package com.formatcheck.backend.service;

import java.nio.file.Path;

public record ResultWorkspace(
        String folderName,
        Path root,
        Path checkedFileDir,
        Path standardFileDir,
        Path ruleExtractionDir,
        Path checkResultDir,
        Path annotatedSourceDir) {

    public Path formatRuleOutput() {
        return ruleExtractionDir.resolve("format_rule.json");
    }

    public Path checkResultOutput() {
        return checkResultDir.resolve("format_check_result.json");
    }

    public Path analysisOutput() {
        return checkResultDir.resolve("format_check_analysis.docx");
    }

    public Path annotatedOutput(String checkedFilename) {
        String baseName = stripExtension(checkedFilename);
        return annotatedSourceDir.resolve(baseName + "_格式检查批注版.docx");
    }

    private static String stripExtension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : String.valueOf(filename);
    }
}
