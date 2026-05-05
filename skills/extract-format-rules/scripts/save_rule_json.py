#!/usr/bin/env python3
"""Clean, validate, and save extracted format-rule JSON."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REQUIRED_TOP_LEVEL = ("file_analysis", "rules")
REQUIRED_RULE_FIELDS = (
    "rule_id",
    "category",
    "rule_name",
    "scope",
    "requirement",
    "check_logic",
    "expected_value",
    "source_text",
    "source_type",
    "confidence",
    "need_manual_confirmation",
    "severity",
)


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


def validate(data: dict) -> None:
    for key in REQUIRED_TOP_LEVEL:
        if key not in data:
            raise SystemExit(f"Missing top-level field: {key}")
    if not isinstance(data["file_analysis"], dict):
        raise SystemExit("file_analysis must be an object.")
    if not isinstance(data["rules"], list) or not data["rules"]:
        raise SystemExit("rules must be a non-empty array.")
    for index, rule in enumerate(data["rules"], start=1):
        if not isinstance(rule, dict):
            raise SystemExit(f"Rule {index} must be an object.")
        missing = [field for field in REQUIRED_RULE_FIELDS if field not in rule]
        if missing:
            raise SystemExit(f"Rule {index} missing fields: {', '.join(missing)}")
        rule["rule_id"] = f"R{index:03d}"
        if not isinstance(rule["need_manual_confirmation"], bool):
            raise SystemExit(f"{rule['rule_id']} need_manual_confirmation must be boolean.")


def slugify(value: str) -> str:
    stem = Path(value).stem if value else "format_rules"
    slug = re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]+", "_", stem).strip("_")
    return slug or "format_rules"


def output_path(output_dir: str, source_file: str | None, output_name: str | None) -> Path:
    directory = Path(output_dir).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    if output_name:
        name = output_name
    else:
        name = f"{slugify(source_file or 'format_rules')}_format_rules.json"
    if not name.lower().endswith(".json"):
        name += ".json"
    return directory / name


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", help="Raw LLM output file. Reads stdin when omitted.")
    parser.add_argument("--output-dir", required=True, help="Directory for the saved JSON file.")
    parser.add_argument("--source-file", help="Original standard/specification file path.")
    parser.add_argument("--output-name", help="Optional output JSON filename.")
    args = parser.parse_args()

    data = extract_json(read_text(args.input))
    if args.source_file:
        data.setdefault("file_analysis", {})
        data["file_analysis"].setdefault("file_name", Path(args.source_file).name)
        data["file_analysis"].setdefault("file_type", Path(args.source_file).suffix.lstrip("."))
    validate(data)

    path = output_path(args.output_dir, args.source_file, args.output_name)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    json.loads(path.read_text(encoding="utf-8"))
    print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
