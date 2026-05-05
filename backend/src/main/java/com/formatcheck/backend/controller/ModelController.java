package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.ModelOption;
import com.formatcheck.backend.service.ModelCatalogService;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelCatalogService modelCatalogService;

    public ModelController(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    @GetMapping
    public ApiResponse<List<ModelOption>> listModels() throws IOException {
        return ApiResponse.ok(modelCatalogService.listModels());
    }
}
