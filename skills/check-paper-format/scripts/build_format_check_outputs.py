#!/usr/bin/env python3
"""Build format-check report and annotated DOCX from LLM JSON output."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree as ET

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
CT = "http://schemas.openxmlformats.org/package/2006/content-types"
OFFICE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

ET.register_namespace("w", W)
ET.register_namespace("r", R)

REQUIRED_TOP_LEVEL = ("paper_analysis", "statistics", "issues")
REQUIRED_ISSUE_FIELDS = (
    "issue_id",
    "category",
    "severity",
    "location",
    "rule_id",
    "rule_name",
    "expected",
    "actual",
    "problem",
    "reason",
    "suggestion",
    "comment_text",
    "confidence",
    "need_manual_confirmation",
)
COMMENT_LABELS = ("错误原因：", "规范要求：", "修改建议：")


def qn(namespace: str, tag: str) -> str:
    return f"{{{namespace}}}{tag}"


def read_text(path: str | None) -> str:
    if path:
        return Path(path).read_text(encoding="utf-8")
    return sys.stdin.read()


def extract_json(raw: str) -> dict:
    text = raw.strip()
    fence = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.S | re.I)
    if fence:
        text = fence.group(1)
    else:
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            text = text[start : end + 1]
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise SystemExit("JSON root must be an object.")
    return data


def validate_and_normalize(data: dict) -> dict:
    for key in REQUIRED_TOP_LEVEL:
        if key not in data:
            raise SystemExit(f"Missing top-level field: {key}")
    if not isinstance(data["paper_analysis"], dict):
        raise SystemExit("paper_analysis must be an object.")
    if not isinstance(data["statistics"], dict):
        raise SystemExit("statistics must be an object.")
    if not isinstance(data["issues"], list):
        raise SystemExit("issues must be an array.")

    for index, issue in enumerate(data["issues"], start=1):
        if not isinstance(issue, dict):
            raise SystemExit(f"Issue {index} must be an object.")
        issue["issue_id"] = f"I{index:03d}"
        issue.setdefault("location", {})
        if not isinstance(issue["location"], dict):
            raise SystemExit(f"{issue['issue_id']} location must be an object.")
        missing = [field for field in REQUIRED_ISSUE_FIELDS if field not in issue]
        if missing:
            raise SystemExit(f"{issue['issue_id']} missing fields: {', '.join(missing)}")
        if not isinstance(issue["need_manual_confirmation"], bool):
            raise SystemExit(f"{issue['issue_id']} need_manual_confirmation must be boolean.")
        comment_text = str(issue.get("comment_text", ""))
        missing_labels = [label for label in COMMENT_LABELS if label not in comment_text]
        if missing_labels:
            raise SystemExit(
                f"{issue['issue_id']} comment_text missing fixed labels: {', '.join(missing_labels)}"
            )
        if not issue.get("anchor_text"):
            issue["anchor_text"] = issue["location"].get("quote", "")

    issues = data["issues"]
    data["statistics"]["total_issues"] = len(issues)
    data["statistics"]["by_category"] = dict(Counter(i.get("category", "other_format") for i in issues))
    data["statistics"]["by_severity"] = dict(Counter(i.get("severity", "medium") for i in issues))
    data["statistics"]["need_manual_confirmation"] = sum(
        1 for i in issues if i.get("need_manual_confirmation")
    )
    return data


def value_to_text(value: object) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False)


def build_markdown_report(data: dict, placed_count: int | None = None) -> str:
    analysis = data["paper_analysis"]
    stats = data["statistics"]
    issues = data["issues"]
    lines = [
        "# 论文格式错误统计分析",
        "",
        f"- 规范文件: {analysis.get('standard_file', '')}",
        f"- 待检论文: {analysis.get('paper_file', '')}",
        f"- 文件类型: {analysis.get('paper_type', '')}",
        f"- 检查方法: {analysis.get('selected_parser', '')}",
        f"- 检查摘要: {analysis.get('check_summary', '')}",
        "",
        "## 统计概览",
        "",
        f"- 错误总数: {stats.get('total_issues', 0)}",
        f"- 需要人工确认: {stats.get('need_manual_confirmation', 0)}",
    ]
    if placed_count is not None:
        lines.append(f"- 已写入批注: {placed_count}")
    lines.extend(["", "### 按严重程度", ""])
    for key, count in stats.get("by_severity", {}).items():
        lines.append(f"- {key}: {count}")
    lines.extend(["", "### 按类别", ""])
    for key, count in stats.get("by_category", {}).items():
        lines.append(f"- {key}: {count}")

    lines.extend(
        [
            "",
            "## 错误明细",
            "",
            "| ID | 严重程度 | 类别 | 位置 | 问题 | 修改建议 |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for issue in issues:
        location = issue.get("location", {})
        location_text = " / ".join(
            str(part)
            for part in (
                location.get("page"),
                location.get("section"),
                location.get("paragraph_index"),
                location.get("quote") or issue.get("anchor_text"),
            )
            if part not in (None, "")
        )
        row = [
            issue.get("issue_id", ""),
            issue.get("severity", ""),
            issue.get("category", ""),
            location_text.replace("|", "\\|"),
            issue.get("problem", "").replace("|", "\\|"),
            issue.get("suggestion", "").replace("|", "\\|"),
        ]
        lines.append("| " + " | ".join(row) + " |")

    lines.extend(["", "## 详细原因与依据", ""])
    for issue in issues:
        lines.extend(
            [
                f"### {issue.get('issue_id')} {issue.get('rule_name')}",
                "",
                f"- 规则编号: {issue.get('rule_id')}",
                f"- 应符合: {value_to_text(issue.get('expected'))}",
                f"- 实际情况: {value_to_text(issue.get('actual'))}",
                f"- 错误原因: {issue.get('reason')}",
                f"- 改正建议: {issue.get('suggestion')}",
                f"- 置信度: {issue.get('confidence')}",
                f"- 需要人工确认: {issue.get('need_manual_confirmation')}",
                "",
            ]
        )
    return "\n".join(lines)


def add_run(paragraph: ET.Element, text: str, bold: bool = False, size: str | None = None) -> None:
    run = ET.SubElement(paragraph, qn(W, "r"))
    if bold or size:
        props = ET.SubElement(run, qn(W, "rPr"))
        if bold:
            ET.SubElement(props, qn(W, "b"))
        if size:
            ET.SubElement(props, qn(W, "sz"), {qn(W, "val"): size})
    t = ET.SubElement(run, qn(W, "t"))
    t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    t.text = text


def write_simple_docx(markdown: str, path: Path) -> None:
    document = ET.Element(qn(W, "document"))
    body = ET.SubElement(document, qn(W, "body"))
    for raw_line in markdown.splitlines():
        line = raw_line.strip()
        if not line:
            ET.SubElement(body, qn(W, "p"))
            continue
        paragraph = ET.SubElement(body, qn(W, "p"))
        if line.startswith("# "):
            add_run(paragraph, line[2:], bold=True, size="32")
        elif line.startswith("## "):
            add_run(paragraph, line[3:], bold=True, size="28")
        elif line.startswith("### "):
            add_run(paragraph, line[4:], bold=True, size="24")
        elif line.startswith("|"):
            cells = [cell.strip() for cell in line.strip("|").split("|")]
            add_run(paragraph, "\t".join(cells))
        else:
            add_run(paragraph, line)
    sect_pr = ET.SubElement(body, qn(W, "sectPr"))
    ET.SubElement(sect_pr, qn(W, "pgSz"), {qn(W, "w"): "11906", qn(W, "h"): "16838"})
    ET.SubElement(
        sect_pr,
        qn(W, "pgMar"),
        {
            qn(W, "top"): "1440",
            qn(W, "right"): "1440",
            qn(W, "bottom"): "1440",
            qn(W, "left"): "1440",
            qn(W, "header"): "720",
            qn(W, "footer"): "720",
            qn(W, "gutter"): "0",
        },
    )

    content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
    package_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", package_rels)
        zf.writestr("word/document.xml", ET.tostring(document, encoding="utf-8", xml_declaration=True))


def parse_xml_from_zip(parts: dict[str, bytes], name: str) -> ET.Element:
    return ET.fromstring(parts[name])


def ensure_comments_part(parts: dict[str, bytes]) -> tuple[ET.Element, int]:
    if "word/comments.xml" in parts:
        root = ET.fromstring(parts["word/comments.xml"])
        ids = [
            int(comment.get(qn(W, "id"), "0"))
            for comment in root.findall(qn(W, "comment"))
            if str(comment.get(qn(W, "id"), "0")).isdigit()
        ]
        return root, (max(ids) + 1 if ids else 0)
    return ET.Element(qn(W, "comments")), 0


def ensure_document_comments_relationship(parts: dict[str, bytes]) -> None:
    rel_name = "word/_rels/document.xml.rels"
    if rel_name in parts:
        root = ET.fromstring(parts[rel_name])
    else:
        root = ET.Element(qn(PKG_REL, "Relationships"))

    for rel in root.findall(qn(PKG_REL, "Relationship")):
        if rel.get("Type") == f"{OFFICE_REL}/comments":
            parts[rel_name] = ET.tostring(root, encoding="utf-8", xml_declaration=True)
            return

    existing_ids = {rel.get("Id", "") for rel in root.findall(qn(PKG_REL, "Relationship"))}
    index = 1
    while f"rId{index}" in existing_ids:
        index += 1
    ET.SubElement(
        root,
        qn(PKG_REL, "Relationship"),
        {
            "Id": f"rId{index}",
            "Type": f"{OFFICE_REL}/comments",
            "Target": "comments.xml",
        },
    )
    parts[rel_name] = ET.tostring(root, encoding="utf-8", xml_declaration=True)


def ensure_comments_content_type(parts: dict[str, bytes]) -> None:
    root = ET.fromstring(parts["[Content_Types].xml"])
    for override in root.findall(qn(CT, "Override")):
        if override.get("PartName") == "/word/comments.xml":
            parts["[Content_Types].xml"] = ET.tostring(root, encoding="utf-8", xml_declaration=True)
            return
    ET.SubElement(
        root,
        qn(CT, "Override"),
        {
            "PartName": "/word/comments.xml",
            "ContentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
        },
    )
    parts["[Content_Types].xml"] = ET.tostring(root, encoding="utf-8", xml_declaration=True)


def paragraph_text(paragraph: ET.Element) -> str:
    return "".join(node.text or "" for node in paragraph.iter(qn(W, "t")))


def find_anchor_paragraph(document_root: ET.Element, issue: dict) -> ET.Element | None:
    anchor = str(issue.get("anchor_text") or issue.get("location", {}).get("quote") or "").strip()
    paragraphs = list(document_root.iter(qn(W, "p")))
    if anchor:
        for paragraph in paragraphs:
            if anchor in paragraph_text(paragraph):
                return paragraph

    paragraph_index = issue.get("location", {}).get("paragraph_index")
    if paragraph_index not in (None, ""):
        try:
            idx = int(paragraph_index) - 1
            if 0 <= idx < len(paragraphs):
                return paragraphs[idx]
        except ValueError:
            return None
    return None


def append_comment(comments_root: ET.Element, comment_id: int, text: str) -> None:
    comment = ET.SubElement(
        comments_root,
        qn(W, "comment"),
        {
            qn(W, "id"): str(comment_id),
            qn(W, "author"): "LLM Format Check",
            qn(W, "date"): datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        },
    )
    paragraph = ET.SubElement(comment, qn(W, "p"))
    add_run(paragraph, text)


def mark_paragraph_with_comment(paragraph: ET.Element, comment_id: int) -> None:
    start = ET.Element(qn(W, "commentRangeStart"), {qn(W, "id"): str(comment_id)})
    end = ET.Element(qn(W, "commentRangeEnd"), {qn(W, "id"): str(comment_id)})
    ref_run = ET.Element(qn(W, "r"))
    ET.SubElement(ref_run, qn(W, "commentReference"), {qn(W, "id"): str(comment_id)})

    insert_at = 1 if len(paragraph) and paragraph[0].tag == qn(W, "pPr") else 0
    paragraph.insert(insert_at, start)
    paragraph.append(end)
    paragraph.append(ref_run)


def annotate_docx(source: Path, target: Path, issues: list[dict]) -> tuple[int, list[str]]:
    if source.suffix.lower() != ".docx":
        raise SystemExit("Annotated comment output requires a .docx paper source.")
    target.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(source, "r") as zin:
        parts = {info.filename: zin.read(info.filename) for info in zin.infolist()}
    if "word/document.xml" not in parts:
        raise SystemExit("Invalid DOCX: missing word/document.xml")

    document_root = parse_xml_from_zip(parts, "word/document.xml")
    comments_root, next_id = ensure_comments_part(parts)
    unplaced: list[str] = []
    placed = 0

    for issue in issues:
        paragraph = find_anchor_paragraph(document_root, issue)
        if paragraph is None:
            unplaced.append(issue.get("issue_id", "unknown"))
            continue
        comment_id = next_id
        next_id += 1
        append_comment(comments_root, comment_id, issue.get("comment_text", issue.get("problem", "")))
        mark_paragraph_with_comment(paragraph, comment_id)
        placed += 1

    parts["word/document.xml"] = ET.tostring(document_root, encoding="utf-8", xml_declaration=True)
    if placed:
        parts["word/comments.xml"] = ET.tostring(comments_root, encoding="utf-8", xml_declaration=True)
        ensure_document_comments_relationship(parts)
        ensure_comments_content_type(parts)

    with tempfile.NamedTemporaryFile(delete=False, suffix=".docx") as tmp:
        temp_path = Path(tmp.name)
    try:
        with zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for name, content in parts.items():
                zout.writestr(name, content)
        shutil.move(str(temp_path), target)
    finally:
        if temp_path.exists():
            temp_path.unlink()

    return placed, unplaced


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", help="Raw LLM JSON output file. Reads stdin when omitted.")
    parser.add_argument("--paper-docx", help="Original DOCX paper to annotate.")
    parser.add_argument("--output-dir", required=True, help="Directory for generated files.")
    parser.add_argument("--analysis-name", default="format_check_analysis.docx")
    parser.add_argument("--annotated-name", default="annotated_paper.docx")
    parser.add_argument("--json-name", default="format_check_result.json")
    args = parser.parse_args()

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    data = validate_and_normalize(extract_json(read_text(args.input)))
    json_path = output_dir / args.json_name
    json_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    json.loads(json_path.read_text(encoding="utf-8"))

    placed = None
    unplaced: list[str] = []
    annotated_path = None
    if args.paper_docx:
        annotated_path = output_dir / args.annotated_name
        placed, unplaced = annotate_docx(Path(args.paper_docx), annotated_path, data["issues"])

    markdown = build_markdown_report(data, placed)
    md_path = output_dir / Path(args.analysis_name).with_suffix(".md").name
    md_path.write_text(markdown, encoding="utf-8")

    analysis_path = output_dir / args.analysis_name
    if analysis_path.suffix.lower() != ".docx":
        analysis_path = analysis_path.with_suffix(".docx")
    write_simple_docx(markdown, analysis_path)

    result = {
        "json": str(json_path),
        "analysis_docx": str(analysis_path),
        "analysis_markdown": str(md_path),
        "annotated_docx": str(annotated_path) if annotated_path else None,
        "comments_placed": placed,
        "comments_unplaced": unplaced,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
