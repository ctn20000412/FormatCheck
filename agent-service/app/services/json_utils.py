from __future__ import annotations

import json


def extract_json_payload(raw_text: str) -> str:
    text = raw_text.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        text = "\n".join(lines).strip()

    start_candidates = [index for index in (text.find("{"), text.find("[")) if index != -1]
    if not start_candidates:
        raise ValueError("No JSON object found in LLM response")
    start_index = min(start_candidates)
    return text[start_index:].strip()


def load_json_object(raw_text: str):
    return json.loads(extract_json_payload(raw_text))
