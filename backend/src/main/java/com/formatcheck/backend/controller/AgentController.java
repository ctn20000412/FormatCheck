package com.formatcheck.backend.controller;

import com.formatcheck.backend.common.ApiResponse;
import com.formatcheck.backend.dto.response.AgentProviderVO;
import com.formatcheck.backend.service.AgentProviderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentProviderService agentProviderService;

    public AgentController(AgentProviderService agentProviderService) {
        this.agentProviderService = agentProviderService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<AgentProviderVO>> listProviders() {
        return ApiResponse.success(agentProviderService.listProviders());
    }
}
