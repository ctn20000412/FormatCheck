# Format Check Output Schema

Top-level object:

```json
{
  "paper_analysis": {},
  "statistics": {},
  "issues": []
}
```

`paper_analysis` fields:

| Field | Type | Required |
| --- | --- | --- |
| `standard_file` | string | yes |
| `paper_file` | string | yes |
| `paper_type` | string | yes |
| `selected_parser` | string | yes |
| `check_summary` | string | yes |

`statistics` fields:

| Field | Type | Required |
| --- | --- | --- |
| `total_issues` | number | yes |
| `by_category` | object | yes |
| `by_severity` | object | yes |
| `need_manual_confirmation` | number | yes |
| `checked_rule_count` | number | no |

`issues[]` fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `issue_id` | string | yes | Sequential ID like `I001`. |
| `category` | string | yes | Controlled category from the prompt. |
| `severity` | string | yes | `high`, `medium`, or `low`. |
| `location` | object | yes | Include page, section, paragraph, object type, and quote when known. |
| `anchor_text` | string | no | Exact paper text for Word comment placement. |
| `rule_id` | string | yes | Rule ID from extracted rule JSON, or stable local rule ID. |
| `rule_name` | string | yes | Short rule name. |
| `expected` | string/object/array | yes | Required format. |
| `actual` | string/object/array | yes | Observed format. |
| `problem` | string | yes | Error description. |
| `reason` | string | yes | Why it violates the rule. |
| `suggestion` | string | yes | Correction advice. |
| `comment_text` | string | yes | Text inserted into the annotated paper. Must use `错误原因：...` + `规范要求：...` + `修改建议：...`. |
| `confidence` | string | yes | `high`, `medium`, or `low`. |
| `need_manual_confirmation` | boolean | yes | True when location/evidence is uncertain. |

Generated files:

| File | Purpose |
| --- | --- |
| `format_check_result.json` | Normalized machine-readable result. |
| `format_check_analysis.docx` | Human-readable error statistics analysis document. |
| `format_check_analysis.md` | Markdown companion for quick preview. |
| `annotated_paper.docx` | Original paper copy with Word comments placed at matching paragraphs. |
