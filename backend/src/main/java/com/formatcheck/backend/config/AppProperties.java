package com.formatcheck.backend.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "format-check")
public class AppProperties {
    private String storageRoot = "../results";
    private String pythonExecutable = "python";
    private String pythonScript = "../agent-service/app/main.py";
    private String modelConfig = "../agent-service/config/llm_providers.json";
    private long pythonTimeoutSeconds = 300;
    private String agentBaseUrl = "http://127.0.0.1:8001";
    private long agentTimeoutSeconds = 300;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Path getStorageRootPath() {
        return resolveLocalPath(storageRoot);
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getPythonScript() {
        return pythonScript;
    }

    public void setPythonScript(String pythonScript) {
        this.pythonScript = pythonScript;
    }

    public String getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(String modelConfig) {
        this.modelConfig = modelConfig;
    }

    public Path getModelConfigPath() {
        return resolveLocalPath(modelConfig);
    }

    public long getPythonTimeoutSeconds() {
        return pythonTimeoutSeconds;
    }

    public void setPythonTimeoutSeconds(long pythonTimeoutSeconds) {
        this.pythonTimeoutSeconds = pythonTimeoutSeconds;
    }

    public Path getPythonScriptPath() {
        return resolveLocalPath(pythonScript);
    }

    public String getAgentBaseUrl() {
        return agentBaseUrl;
    }

    public void setAgentBaseUrl(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl;
    }

    public long getAgentTimeoutSeconds() {
        return agentTimeoutSeconds;
    }

    public void setAgentTimeoutSeconds(long agentTimeoutSeconds) {
        this.agentTimeoutSeconds = agentTimeoutSeconds;
    }

    public Path getLocalModelConfigPath() {
        Path modelConfigPath = getModelConfigPath();
        Path parent = modelConfigPath.getParent();
        if (parent == null) {
            return Path.of("llm_providers.local.json").toAbsolutePath().normalize();
        }
        return parent.resolve("llm_providers.local.json").normalize();
    }

    private Path resolveLocalPath(String value) {
        Path configured = Path.of(value);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path direct = configured.toAbsolutePath().normalize();
        if (Files.exists(direct)) {
            return direct;
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (configured.getNameCount() > 1 && "..".equals(configured.getName(0).toString())) {
            Path withoutLeadingParent = configured.subpath(1, configured.getNameCount());
            return cwd.resolve(withoutLeadingParent).normalize();
        }

        Path fromParent = cwd.resolve("..").resolve(configured).normalize();
        if (Files.exists(fromParent)) {
            return fromParent;
        }

        return direct;
    }
}
