package com.formatcheck.backend.config;

import com.formatcheck.backend.domain.enums.AgentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Prompt prompt = new Prompt();
    private final Agent agent = new Agent();

    public Storage getStorage() {
        return storage;
    }

    public Prompt getPrompt() {
        return prompt;
    }

    public Agent getAgent() {
        return agent;
    }

    public static class Storage {
        private String root = "storage/sessions";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Prompt {
        private String extractTemplate;
        private String checkTemplate;

        public String getExtractTemplate() {
            return extractTemplate;
        }

        public void setExtractTemplate(String extractTemplate) {
            this.extractTemplate = extractTemplate;
        }

        public String getCheckTemplate() {
            return checkTemplate;
        }

        public void setCheckTemplate(String checkTemplate) {
            this.checkTemplate = checkTemplate;
        }
    }

    public static class Agent {
        private String mode = "mock";
        private String baseUrl = "http://localhost:9000";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 180;
        private AgentProvider defaultProvider = AgentProvider.DEEPSEEK;
        private final Map<String, Provider> providers = new LinkedHashMap<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public AgentProvider getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(AgentProvider defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        public Map<String, Provider> getProviders() {
            return providers;
        }
    }

    public static class Provider {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private final Map<String, String> extra = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public Map<String, String> getExtra() {
            return extra;
        }
    }
}
