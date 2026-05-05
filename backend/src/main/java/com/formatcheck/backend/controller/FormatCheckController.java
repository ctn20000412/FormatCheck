package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.CheckTaskResponse;
import com.formatcheck.backend.service.FormatCheckService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks")
public class FormatCheckController {
    private final FormatCheckService service;

    public FormatCheckController(FormatCheckService service) {
        this.service = service;
    }

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CheckTaskResponse> runCheck(
            @RequestParam String provider,
            @RequestParam String model,
            @RequestParam(defaultValue = "base_format") String workflow,
            @RequestParam MultipartFile standardFile,
            @RequestParam MultipartFile checkedFile,
            @RequestParam(required = false) String resultFolderName)
            throws Exception {
        return ApiResponse.ok(service.runCheck(provider, model, workflow, standardFile, checkedFile, resultFolderName));
    }
}
