# Rule JSON Schema

Top-level object:

```json
{
  "file_analysis": {
    "file_name": "string",
    "file_type": "string",
    "selected_parser": "string",
    "analysis_summary": "string"
  },
  "rules": []
}
```

Rule object:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `rule_id` | string | yes | Sequential ID like `R001`. |
| `category` | string | yes | Use the controlled category list in `SKILL.md`. |
| `rule_name` | string | yes | Short, human-readable title. |
| `scope` | string | yes | Applies to whole document, section, heading level, table, figure, etc. |
| `requirement` | string | yes | Complete rule statement. |
| `check_logic` | string | yes | How to evaluate compliance. |
| `expected_value` | string/object/array | yes | Normalized target value or exact required format. |
| `source_text` | string | yes | Short source evidence. |
| `source_type` | string | yes | `explicit_text`, `template_example`, or `inferred_from_context`. |
| `confidence` | string | yes | `high`, `medium`, or `low`. |
| `need_manual_confirmation` | boolean | yes | True for ambiguous, inferred, or hard-to-check rules. |
| `severity` | string | yes | `high`, `medium`, or `low`. |

Recommended category mapping:

| Category | Use for |
| --- | --- |
| `document_structure` | Required sections, order, abstracts, catalog, acknowledgements. |
| `page_setup` | Paper size, margins, orientation, page numbers, headers, footers, printing. |
| `font_and_size` | Font family, font size, bold, italic, color. |
| `paragraph_format` | Alignment, indentation, spacing, line spacing. |
| `heading_numbering` | Heading levels, numbering, title hierarchy, table of contents numbering. |
| `table_format` | Table captions, numbering, borders, width, placement. |
| `figure_format` | Figure captions, numbering, image placement, resolution. |
| `citation_reference` | In-text citations, notes, bibliography/reference list. |
| `appendix` | Appendix names, ordering, numbering, placement. |
| `submission_requirement` | Required file format, naming, print/bind/upload requirements. |
| `other_format` | Format-related requirements that do not fit above. |
