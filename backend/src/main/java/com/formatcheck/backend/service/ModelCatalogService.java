package com.formatcheck.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.dto.ModelOption;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalogService {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public ModelCatalogService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<ModelOption> listModels() throws IOException {
        return readModels(properties.getModelConfigPath());
    }

    public boolean supports(String provider, String model) throws IOException {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return false;
        }
        return listModels().stream().anyMatch(option -> provider.equals(option.provider()) && model.equals(option.model()));
    }

    public void requireSupported(String provider, String model) throws IOException {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("模型厂商不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("模型型号不能为空");
        }
        if (!supports(provider, model)) {
            throw new IllegalArgumentException("不支持的模型厂商或型号: " + provider + "/" + model);
        }
    }

    private List<ModelOption> readModels(Path catalogPath) throws IOException {
        JsonNode root = objectMapper.readTree(catalogPath.toFile());
        JsonNode providers = root.path("providers");
        List<ModelOption> options = new ArrayList<>();
        if (!providers.isArray()) {
            return options;
        }
        for (JsonNode providerNode : providers) {
            String provider = providerNode.path("id").asText("");
            if (provider.isBlank()) {
                continue;
            }
            JsonNode models = providerNode.path("models");
            if (!models.isArray()) {
                continue;
            }
            for (JsonNode modelNode : models) {
                String model = modelNode.isTextual() ? modelNode.asText("") : modelNode.path("id").asText("");
                if (!model.isBlank()) {
                    options.add(new ModelOption(provider, model));
                }
            }
        }
        return options;
    }
}
