from __future__ import annotations

from collections import Counter

from app.models.contracts import CheckIssue


class ReportBuilder:
    def build_summary(self, issues: list[CheckIssue]) -> dict[str, int]:
        counter = Counter(issue.severity.upper() for issue in issues)
        return {
            "totalIssues": len(issues),
            "criticalIssues": counter.get("CRITICAL", 0),
            "majorIssues": counter.get("MAJOR", 0),
            "minorIssues": counter.get("MINOR", 0),
        }
