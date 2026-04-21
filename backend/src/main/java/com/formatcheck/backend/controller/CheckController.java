package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.request.CheckRequest;
import com.formatcheck.backend.dto.response.CheckResultVO;
import com.formatcheck.backend.dto.response.TaskVO;
import com.formatcheck.backend.service.CheckService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping("/checks")
    public ApiResponse<TaskVO> execute(@Valid @RequestBody CheckRequest request) {
        return ApiResponse.success(checkService.executeCheck(
                request.sessionId(),
                request.ruleSessionId(),
                request.checkLevel(),
                request.provider(),
                request.model()
        ));
    }

    @GetMapping("/checks/{sessionId}/result")
    public ApiResponse<CheckResultVO> result(@PathVariable String sessionId) {
        return ApiResponse.success(checkService.getResult(sessionId));
    }
}
