package com.formatcheck.backend.service;

import com.formatcheck.backend.config.AppProperties;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private final AppProperties appProperties;

    public PromptTemplateService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String getRuleExtractPrompt() {
        return appProperties.getPrompt().getExtractTemplate();
    }

    public String getFormatCheckPrompt() {
        return appProperties.getPrompt().getCheckTemplate();
    }
}
