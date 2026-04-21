package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.request.ConfirmRuleRequest;
import com.formatcheck.backend.dto.request.RuleExtractRequest;
import com.formatcheck.backend.dto.response.RuleConfirmVO;
import com.formatcheck.backend.dto.response.RuleVO;
import com.formatcheck.backend.dto.response.TaskVO;
import com.formatcheck.backend.service.RuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("/extract")
    public ApiResponse<TaskVO> extract(@Valid @RequestBody RuleExtractRequest request) {
        return ApiResponse.success(ruleService.extractRules(
                request.sessionId(),
                request.schemaVersion(),
                request.provider(),
                request.model()
        ));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<RuleVO> getRules(@PathVariable String sessionId) {
        return ApiResponse.success(ruleService.getRules(sessionId));
    }

    @PostMapping("/{sessionId}/confirm")
    public ApiResponse<RuleConfirmVO> confirm(@PathVariable String sessionId, @Valid @RequestBody ConfirmRuleRequest request) {
        return ApiResponse.success(ruleService.confirmRules(sessionId, request.ruleContent(), request.versionNote()));
    }
}
