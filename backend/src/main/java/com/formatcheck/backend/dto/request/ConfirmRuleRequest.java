package com.formatcheck.backend.dto.request;

import com.formatcheck.backend.domain.model.RuleItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmRuleRequest(
        @NotBlank(message = "sessionId 不能为空") String sessionId,
        String versionNote,
        @Valid @NotEmpty(message = "ruleContent 不能为空") List<RuleItem> ruleContent
) {
}
