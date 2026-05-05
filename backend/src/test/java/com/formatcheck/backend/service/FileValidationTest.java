package com.formatcheck.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FileValidationTest {

    @Test
    void acceptsOnlySupportedStandardFiles() {
        assertDoesNotThrow(() -> FileValidation.requireStandardFile("rules.pdf"));
        assertDoesNotThrow(() -> FileValidation.requireStandardFile("rules.doc"));
        assertDoesNotThrow(() -> FileValidation.requireStandardFile("rules.docx"));
        assertDoesNotThrow(() -> FileValidation.requireStandardFile("rules.md"));

        assertThrows(IllegalArgumentException.class, () -> FileValidation.requireStandardFile("rules.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileValidation.requireStandardFile("rules.xlsx"));
    }

    @Test
    void acceptsOnlyDocxCheckedFiles() {
        assertDoesNotThrow(() -> FileValidation.requireCheckedFile("paper.docx"));

        assertThrows(IllegalArgumentException.class, () -> FileValidation.requireCheckedFile("paper.pdf"));
        assertThrows(IllegalArgumentException.class, () -> FileValidation.requireCheckedFile("paper.doc"));
    }
}
