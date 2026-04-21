package com.formatcheck.backend.service;

import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.config.AppProperties;
import com.formatcheck.backend.domain.enums.AgentProvider;
import com.formatcheck.backend.dto.response.AgentProviderVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentProviderService {

    private final AppProperties appProperties;

    public AgentProviderService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public AgentExecutionOptions resolve(AgentProvider requestedProvider, String requestedModel) {
        AgentProvider provider = requestedProvider != null ? requestedProvider : appProperties.getAgent().getDefaultProvider();
        if (provider == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "未配置默认模型提供商");
        }
        AppProperties.Provider providerConfig = appProperties.getAgent().getProviders().get(normalize(provider));
        if (providerConfig == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "未找到提供商配置: " + provider.name());
        }
        if (!providerConfig.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "提供商未启用: " + provider.name());
        }

        String model = StringUtils.hasText(requestedModel) ? requestedModel : providerConfig.getDefaultModel();
        if (!StringUtils.hasText(model)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "未配置默认模型: " + provider.name());
        }

        return new AgentExecutionOptions(
                provider,
                model,
                providerConfig.getBaseUrl(),
                providerConfig.getApiKey(),
                Map.copyOf(providerConfig.getExtra())
        );
    }

    public List<AgentProviderVO> listProviders() {
        return Arrays.stream(AgentProvider.values())
                .map(provider -> {
                    AppProperties.Provider providerConfig = appProperties.getAgent().getProviders().get(normalize(provider));
                    boolean configured = providerConfig != null;
                    boolean enabled = configured && providerConfig.isEnabled();
                    String defaultModel = configured ? providerConfig.getDefaultModel() : null;
                    return new AgentProviderVO(provider, enabled, defaultModel);
                })
                .toList();
    }

    private String normalize(AgentProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }
}
