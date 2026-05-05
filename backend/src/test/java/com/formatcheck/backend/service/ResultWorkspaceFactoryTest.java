package com.formatcheck.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultWorkspaceFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsFiveBusinessDirectoriesUnderCheckedFileName() throws Exception {
        ResultWorkspaceFactory factory = new ResultWorkspaceFactory(tempDir);

        ResultWorkspace workspace = factory.create("善意想.docx", null);

        assertEquals("善意想", workspace.folderName());
        assertTrue(Files.isDirectory(workspace.checkedFileDir()));
        assertTrue(Files.isDirectory(workspace.standardFileDir()));
        assertTrue(Files.isDirectory(workspace.ruleExtractionDir()));
        assertTrue(Files.isDirectory(workspace.checkResultDir()));
        assertTrue(Files.isDirectory(workspace.annotatedSourceDir()));
    }

    @Test
    void usesExplicitFolderNameBeforeSanitizedFileName() throws Exception {
        ResultWorkspaceFactory factory = new ResultWorkspaceFactory(tempDir);

        ResultWorkspace workspace = factory.create("单奕翔：变化中的永恒.docx", "善意想");

        assertEquals("善意想", workspace.folderName());
        assertTrue(workspace.root().endsWith("善意想"));
    }
}
