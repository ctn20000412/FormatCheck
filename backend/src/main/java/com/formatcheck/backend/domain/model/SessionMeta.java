package com.formatcheck.backend.domain.model;

import com.formatcheck.backend.domain.enums.AgentProvider;
import com.formatcheck.backend.domain.enums.RuleSource;
import com.formatcheck.backend.domain.enums.SessionType;
import com.formatcheck.backend.domain.enums.TaskStatus;

import java.time.OffsetDateTime;

public class SessionMeta {

    private String sessionId;
    private String name;
    private SessionType type;
    private TaskStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer ruleVersion;
    private String standardFileName;
    private String targetFileName;
    private RuleSource ruleSource;
    private String errorMessage;
    private AgentProvider agentProvider;
    private String agentModel;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SessionType getType() {
        return type;
    }

    public void setType(SessionType type) {
        this.type = type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(Integer ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getStandardFileName() {
        return standardFileName;
    }

    public void setStandardFileName(String standardFileName) {
        this.standardFileName = standardFileName;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

    public RuleSource getRuleSource() {
        return ruleSource;
    }

    public void setRuleSource(RuleSource ruleSource) {
        this.ruleSource = ruleSource;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public AgentProvider getAgentProvider() {
        return agentProvider;
    }

    public void setAgentProvider(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    public String getAgentModel() {
        return agentModel;
    }

    public void setAgentModel(String agentModel) {
        this.agentModel = agentModel;
    }
}
