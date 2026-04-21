package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.response.RuleImportVO;
import com.formatcheck.backend.service.RuleService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sessions/{sessionId}/files")
public class RuleImportController {

    private final RuleService ruleService;

    public RuleImportController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping(value = "/rule-final", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RuleImportVO> uploadRuleFile(@PathVariable String sessionId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(ruleService.importFinalRules(sessionId, file));
    }
}
