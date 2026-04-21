from __future__ import annotations

from pathlib import Path

from app.models.contracts import RuleItem
from app.services.json_utils import load_json_object


class RuleFileLoader:
    def load(self, rule_file_path: str) -> list[RuleItem]:
        path = Path(rule_file_path)
        if not path.exists():
            raise FileNotFoundError(f"Rule file not found: {rule_file_path}")

        raw = path.read_text(encoding="utf-8")
        normalized = raw.strip()
        if normalized.startswith("module.exports"):
            normalized = normalized.split("=", 1)[1].strip()
        elif normalized.startswith("export default"):
            normalized = normalized[len("export default"):].strip()
        if normalized.endswith(";"):
            normalized = normalized[:-1].strip()

        data = load_json_object(normalized)
        if not isinstance(data, list):
            raise ValueError("Rule file must contain a rule array")
        return [RuleItem.model_validate(item) for item in data]
