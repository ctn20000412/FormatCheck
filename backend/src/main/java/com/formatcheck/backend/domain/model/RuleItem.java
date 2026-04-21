package com.formatcheck.backend.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record RuleItem(
        @NotBlank(message = "rule_id 不能为空") String rule_id,
        @NotBlank(message = "category 不能为空") String category,
        @NotBlank(message = "scope 不能为空") String scope,
        @NotBlank(message = "rule_name 不能为空") String rule_name,
        @NotBlank(message = "description 不能为空") String description,
        @NotBlank(message = "check_type 不能为空") String check_type,
        @NotEmpty(message = "check_object 不能为空") List<String> check_object,
        @NotNull(message = "expected 不能为空") Map<String, Object> expected,
        @NotBlank(message = "severity 不能为空") String severity,
        @NotBlank(message = "source_text 不能为空") String source_text,
        @NotEmpty(message = "applicable_sections 不能为空") List<String> applicable_sections,
        boolean user_confirmed
) {
}
