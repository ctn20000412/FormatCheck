from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field, field_validator


class RuleItem(BaseModel):
    rule_id: str
    category: str
    scope: str
    rule_name: str
    description: str
    check_type: str
    check_object: list[str]
    expected: dict[str, Any]
    severity: str
    source_text: str
    applicable_sections: list[str]
    user_confirmed: bool = False


class IssueLocation(BaseModel):
    page: int | None = None
    paragraphIndex: int | None = None
    heading: str | None = None


class CheckIssue(BaseModel):
    id: str
    location: IssueLocation
    ruleId: str
    ruleName: str
    sourceText: str
    applicableSections: list[str] = Field(default_factory=list)
    checkObject: list[str] = Field(default_factory=list)
    severity: str
    reason: str
    suggestion: str
    expectedValue: Any = None
    actualValue: Any = None


class RuleExtractRequest(BaseModel):
    sessionId: str
    filePath: str
    outputDir: str
    promptText: str
    schemaVersion: str = "1.0"
    provider: str
    model: str
    apiKey: str | None = None
    apiBaseUrl: str | None = None
    extraConfig: dict[str, str] = Field(default_factory=dict)

    @field_validator("filePath", "outputDir")
    @classmethod
    def validate_paths(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("path cannot be blank")
        return value


class RuleExtractResponse(BaseModel):
    rules: list[RuleItem]
    confidenceNote: str = ""
    unidentifiedItems: list[str] = Field(default_factory=list)


class CheckRequest(BaseModel):
    sessionId: str
    targetFilePath: str
    ruleFilePath: str
    outputDir: str
    promptText: str
    checkLevel: str = "NORMAL"
    provider: str
    model: str
    apiKey: str | None = None
    apiBaseUrl: str | None = None
    extraConfig: dict[str, str] = Field(default_factory=dict)


class CheckResponse(BaseModel):
    summary: dict[str, int]
    issues: list[CheckIssue]
    annotatedFilePath: str


class LlmExecutionContext(BaseModel):
    provider: str
    model: str
    api_key: str | None = None
    api_base_url: str | None = None
    extra_config: dict[str, str] = Field(default_factory=dict)

    def require_api_settings(self) -> tuple[str, str]:
        if not self.api_key:
            raise ValueError(f"API key is required for provider {self.provider}")
        if not self.api_base_url:
            raise ValueError(f"API base URL is required for provider {self.provider}")
        return self.api_key, self.api_base_url


class ParsedDocument(BaseModel):
    file_path: str
    file_name: str
    file_type: str
    plain_text: str
    structure_lines: list[str] = Field(default_factory=list)
    excerpts: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_parts(
        cls,
        file_path: Path,
        file_type: str,
        plain_text: str,
        structure_lines: list[str],
        excerpts: list[str],
        metadata: dict[str, Any] | None = None,
    ) -> "ParsedDocument":
        return cls(
            file_path=str(file_path),
            file_name=file_path.name,
            file_type=file_type,
            plain_text=plain_text,
            structure_lines=structure_lines,
            excerpts=excerpts,
            metadata=metadata or {},
        )
