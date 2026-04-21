package com.formatcheck.backend.dto.request;

import com.formatcheck.backend.domain.enums.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull(message = "type 不能为空") SessionType type,
        @NotBlank(message = "name 不能为空") String name
) {
}
