from __future__ import annotations

import json
from pathlib import Path

from app.models.contracts import LlmExecutionContext, ParsedDocument, RuleExtractRequest, RuleExtractResponse, RuleItem
from app.services.document_parser import DocumentParser
from app.services.json_utils import load_json_object
from app.services.llm_gateway import OpenAiCompatibleGateway


class ExtractorAgent:
    def __init__(self) -> None:
        self.document_parser = DocumentParser()
        self.llm_gateway = OpenAiCompatibleGateway()

    async def run(self, request: RuleExtractRequest) -> RuleExtractResponse:
        parsed_document = self.document_parser.parse(request.filePath)
        llm_context = LlmExecutionContext(
            provider=request.provider,
            model=request.model,
            api_key=request.apiKey,
            api_base_url=request.apiBaseUrl,
            extra_config=request.extraConfig,
        )

        raw = await self.llm_gateway.complete_json(
            context=llm_context,
            system_prompt=self._build_system_prompt(request.schemaVersion),
            user_prompt=self._build_user_prompt(request, parsed_document),
        )
        result = load_json_object(raw)

        rules = [RuleItem.model_validate(item) for item in result.get("rules", [])]
        if not rules:
            raise ValueError("LLM did not return any rules")

        output_dir = Path(request.outputDir)
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / "extract-result.raw.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        return RuleExtractResponse(
            rules=rules,
            confidenceNote=str(result.get("confidenceNote", "")),
            unidentifiedItems=[str(item) for item in result.get("unidentifiedItems", [])],
        )

    def _build_system_prompt(self, schema_version: str) -> str:
        return (
            "你是规则抽取 Agent。"
            "你的任务是从规范文档中抽取可执行的文档格式检查规则。"
            "必须只输出 JSON，不要输出解释、Markdown 或代码块。"
            "返回格式为："
            "{"
            '"rules":[{"rule_id":"","category":"","scope":"","rule_name":"","description":"","check_type":"","check_object":[],"expected":{},"severity":"","source_text":"","applicable_sections":[],"user_confirmed":false}],'
            '"confidenceNote":"","unidentifiedItems":[]'
            "}。"
            f"schemaVersion={schema_version}。"
            "规则要覆盖页面设置、标题样式、正文样式、编号规则、图表规范、特殊章节要求。"
        )

    def _build_user_prompt(self, request: RuleExtractRequest, document: ParsedDocument) -> str:
        structure = "\n".join(document.structure_lines[:200])
        excerpts = "\n".join(f"- {line}" for line in document.excerpts[:30])
        metadata = json.dumps(document.metadata, ensure_ascii=False, indent=2)
        return (
            f"{request.promptText}\n\n"
            f"文件名: {document.file_name}\n"
            f"文件类型: {document.file_type}\n"
            f"文档元数据:\n{metadata}\n\n"
            f"文档结构摘要:\n{structure}\n\n"
            f"文档关键片段:\n{excerpts}\n\n"
            "请抽取 6 到 20 条高价值规则。"
            "每条规则必须能被后续格式检查使用，source_text 必须引用或转述规范原文。"
            "如果文档中有模糊表达，可以在 unidentifiedItems 中指出。"
        )
