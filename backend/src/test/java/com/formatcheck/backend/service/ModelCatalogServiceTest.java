package com.formatcheck.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.dto.ModelOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCatalogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsProviderModelsFromPythonCatalog() throws Exception {
        Path catalog = tempDir.resolve("llm_providers.json");
        Files.writeString(
                catalog,
                """
                {
                  "providers": [
                    {
                      "id": "deepseek",
                      "models": [
                        {"id": "deepseek-v4-flash"},
                        {"id": "deepseek-v4-pro"}
                      ]
                    },
                    {
                      "id": "chatgpt",
                      "models": [
                        {"id": "gpt-5.5"}
                      ]
                    }
                  ]
                }
                """);
        AppProperties properties = new AppProperties();
        properties.setModelConfig(catalog.toString());
        ModelCatalogService service = new ModelCatalogService(properties, new ObjectMapper());

        List<ModelOption> models = service.listModels();

        assertEquals(
                List.of(
                        new ModelOption("deepseek", "deepseek-v4-flash"),
                        new ModelOption("deepseek", "deepseek-v4-pro"),
                        new ModelOption("chatgpt", "gpt-5.5")),
                models);
        assertTrue(service.supports("deepseek", "deepseek-v4-pro"));
    }

    @Test
    void rejectsUnknownProviderOrModel() throws Exception {
        Path catalog = tempDir.resolve("llm_providers.json");
        Files.writeString(
                catalog,
                """
                {
                  "providers": [
                    {"id": "kimi", "models": [{"id": "kimi-k2"}]}
                  ]
                }
                """);
        AppProperties properties = new AppProperties();
        properties.setModelConfig(catalog.toString());
        ModelCatalogService service = new ModelCatalogService(properties, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.requireSupported("glm", "glm-5.1"));
        assertThrows(IllegalArgumentException.class, () -> service.requireSupported("kimi", "missing-model"));
    }
}
