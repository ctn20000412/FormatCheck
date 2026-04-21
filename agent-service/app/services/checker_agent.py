from __future__ import annotations

import json
from pathlib import Path

from app.models.contracts import CheckIssue, CheckRequest, CheckResponse, LlmExecutionContext
from app.services.annotation_writer import AnnotationWriter
from app.services.document_parser import DocumentParser
from app.services.json_utils import load_json_object
from app.services.llm_gateway import OpenAiCompatibleGateway
from app.services.report_builder import ReportBuilder
from app.services.rule_file_loader import RuleFileLoader


class CheckerAgent:
    def __init__(self) -> None:
        self.document_parser = DocumentParser()
        self.rule_file_loader = RuleFileLoader()
        self.llm_gateway = OpenAiCompatibleGateway()
        self.report_builder = ReportBuilder()
        self.annotation_writer = AnnotationWriter()

    async def run(self, request: CheckRequest) -> CheckResponse:
        parsed_document = self.document_parser.parse(request.targetFilePath)
        rules = self.rule_file_loader.load(request.ruleFilePath)
        llm_context = LlmExecutionContext(
            provider=request.provider,
            model=request.model,
            api_key=request.apiKey,
            api_base_url=request.apiBaseUrl,
            extra_config=request.extraConfig,
        )

        raw = await self.llm_gateway.complete_json(
            context=llm_context,
            system_prompt=self._build_system_prompt(request.checkLevel),
            user_prompt=self._build_user_prompt(request, parsed_document, rules),
        )
        result = load_json_object(raw)
        issues = [CheckIssue.model_validate(item) for item in result.get("issues", [])]
        summary = self.report_builder.build_summary(issues)

        output_dir = Path(request.outputDir)
        output_dir.mkdir(parents=True, exist_ok=True)
        annotated_path = output_dir / self._annotated_name(Path(request.targetFilePath))
        self.annotation_writer.write(request.targetFilePath, str(annotated_path), issues)
        (output_dir / "check-result.raw.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        return CheckResponse(
            summary=summary,
            issues=issues,
            annotatedFilePath=str(annotated_path),
        )

    def _build_system_prompt(self, check_level: str) -> str:
        return (
            "你是格式检查 Agent。"
            "你的任务是根据完整规则文件检查目标文档的版式、结构和格式问题。"
            "必须只输出 JSON，不要输出解释、Markdown 或代码块。"
            "返回格式为："
            "{"
            '"issues":[{"id":"","location":{"page":1,"paragraphIndex":1,"heading":""},"ruleId":"","ruleName":"","sourceText":"","applicableSections":[],"checkObject":[],"severity":"MAJOR","reason":"","suggestion":"","expectedValue":"","actualValue":""}]'
            "}。"
            f"checkLevel={check_level}。"
            "只有在存在明确不符合规则的证据时才返回 issue。"
            "severity 只允许 CRITICAL、MAJOR、MINOR。"
        )

    def _build_user_prompt(self, request: CheckRequest, document, rules) -> str:
        structure = "\n".join(document.structure_lines[:250])
        excerpts = "\n".join(f"- {line}" for line in document.excerpts[:40])
        rules_json = json.dumps([rule.model_dump() for rule in rules], ensure_ascii=False, indent=2)
        metadata = json.dumps(document.metadata, ensure_ascii=False, indent=2)
        return (
            f"{request.promptText}\n\n"
            f"目标文件: {document.file_name}\n"
            f"文件类型: {document.file_type}\n"
            f"文档元数据:\n{metadata}\n\n"
            f"规则文件:\n{rules_json}\n\n"
            f"文档结构摘要:\n{structure}\n\n"
            f"关键片段:\n{excerpts}\n\n"
            "请返回严格可定位的问题列表。"
            "location.page、location.paragraphIndex、location.heading 尽可能给出。"
            "reason 要说明为什么不符合规则，suggestion 要给出可执行修改建议。"
        )

    def _annotated_name(self, source_path: Path) -> str:
        if source_path.suffix:
            return f"{source_path.stem}.annotated{source_path.suffix}"
        return f"{source_path.name}.annotated"
