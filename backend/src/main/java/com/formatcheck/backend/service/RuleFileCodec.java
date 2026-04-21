package com.formatcheck.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.formatcheck.backend.common.ApiException;
import com.formatcheck.backend.common.ErrorCode;
import com.formatcheck.backend.domain.model.RuleItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class RuleFileCodec {

    private static final TypeReference<List<RuleItem>> RULE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RuleFileCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void writeJsonRules(Path path, List<RuleItem> rules) {
        try {
            Files.writeString(path, objectMapper.writeValueAsString(rules));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "写入规则 JSON 失败");
        }
    }

    public List<RuleItem> readJsonRules(Path path) {
        try {
            return objectMapper.readValue(Files.readString(path), RULE_LIST_TYPE);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "读取规则 JSON 失败");
        }
    }

    public void writeModuleRules(Path path, List<RuleItem> rules) {
        try {
            String content = "module.exports = " + objectMapper.writeValueAsString(rules) + System.lineSeparator() + ";";
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "写入规则文件失败");
        }
    }

    public List<RuleItem> parseRuleModule(String rawContent) {
        try {
            String normalized = rawContent.trim();
            if (normalized.startsWith("module.exports")) {
                normalized = normalized.substring(normalized.indexOf('=') + 1).trim();
            } else if (normalized.startsWith("export default")) {
                normalized = normalized.substring("export default".length()).trim();
            }
            if (normalized.endsWith(";")) {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            }

            Object loaded = new Yaml().load(normalized);
            return objectMapper.convertValue(loaded, RULE_LIST_TYPE);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "规则结构校验失败");
        }
    }
}
