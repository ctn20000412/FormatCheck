package com.formatcheck.backend.service;

import com.formatcheck.backend.domain.model.CheckIssue;
import com.formatcheck.backend.domain.model.RuleItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAgentClient implements AgentClient {

    @Override
    public ExtractionResult extractRules(String sessionId, Path standardFilePath, Path outputDir, String promptText, String schemaVersion, AgentExecutionOptions options) {
        List<RuleItem> rules = List.of(
                new RuleItem(
                        "R001",
                        "页面设置",
                        "全文",
                        "页边距要求",
                        "全文页边距应符合标准文档要求",
                        "exact",
                        List.of("margin_top", "margin_bottom", "margin_left", "margin_right"),
                        Map.of("margin_top", "2.5cm", "margin_bottom", "2.5cm", "margin_left", "3cm", "margin_right", "2.5cm"),
                        "high",
                        "规范文档中的页面设置条款",
                        List.of("full_document"),
                        false
                ),
                new RuleItem(
                        "R002",
                        "标题样式",
                        "一级标题",
                        "一级标题字体字号要求",
                        "一级标题应使用黑体三号并居中",
                        "hybrid",
                        List.of("font_family", "font_size", "alignment"),
                        Map.of("font_family", "黑体", "font_size", "三号", "alignment", "center"),
                        "high",
                        "规范文档中的一级标题条款",
                        List.of("title_level_1"),
                        false
                ),
                new RuleItem(
                        "R003",
                        "图表规范",
                        "图片与表格",
                        "图表标题要求",
                        "图片和表格需带编号及标题",
                        "semantic",
                        List.of("caption", "numbering"),
                        Map.of("caption", "required", "numbering", "sequential"),
                        "medium",
                        "规范文档中的图表条款",
                        List.of("figures", "tables"),
                        false
                )
        );
        return new ExtractionResult(rules, "mock-agent", List.of());
    }

    @Override
    public CheckExecutionResult executeCheck(String sessionId, Path targetFilePath, Path ruleFilePath, Path outputDir, String promptText, String checkLevel, AgentExecutionOptions options) {
        List<CheckIssue> issues = List.of(
                new CheckIssue(
                        "ISSUE-001",
                        new CheckIssue.Location(1, 2, "1. 项目背景"),
                        "R002",
                        "一级标题字体字号要求",
                        "规范文档中的一级标题条款",
                        List.of("title_level_1"),
                        List.of("alignment"),
                        "MAJOR",
                        "一级标题未居中",
                        "将一级标题设置为居中对齐",
                        "center",
                        "left"
                ),
                new CheckIssue(
                        "ISSUE-002",
                        new CheckIssue.Location(3, 8, "2.1 系统架构"),
                        "R003",
                        "图表标题要求",
                        "规范文档中的图表条款",
                        List.of("figures"),
                        List.of("caption"),
                        "MINOR",
                        "图片缺少规范标题",
                        "为图片补充符合规范的编号和标题",
                        "required",
                        "missing"
                )
        );
        Map<String, Integer> summary = Map.of(
                "totalIssues", 2,
                "criticalIssues", 0,
                "majorIssues", 1,
                "minorIssues", 1
        );
        String fileName = targetFilePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String annotatedName = dotIndex > 0
                ? fileName.substring(0, dotIndex) + ".annotated" + fileName.substring(dotIndex)
                : fileName + ".annotated";
        return new CheckExecutionResult(summary, issues, outputDir.resolve(annotatedName));
    }
}
