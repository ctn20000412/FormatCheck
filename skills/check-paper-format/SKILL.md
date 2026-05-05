---
name: check-paper-format
description: Use when a user provides a document-format standard or rule file plus a thesis, paper, dissertation, or manuscript and needs the paper checked against the standard, with formatting errors summarized in an analysis document and marked in an annotated copy using comments.
---

# Check Paper Format

## Overview

Check a thesis or paper against a formatting standard and produce two deliverables: an error statistics analysis document and an annotated copy of the original paper with comments explaining each formatting issue, cause, and suggested fix.

## Workflow

1. Identify two inputs:
   - Standard source: a formatting guideline file, extracted JSON rules, or text produced by `extract-format-rules`.
   - Paper source: the thesis/paper to check, preferably `.docx` when an annotated copy is required.
2. Read `references/document_io_methods.md`, then extract structure and formatting evidence from the standard and paper.
3. For paper `.docx`, use mature DOCX inspection before LLM checking:
   - Use `python-docx` for document structure, paragraphs, tables, and style names.
   - Use direct WordprocessingML for page margins, paper size, section breaks, inherited styles, fonts, font sizes, spacing, numbering, headers, footers, and page-number fields.
   - Render the DOCX to pages when visual layout or pagination matters.
4. For PDF standards, use text extraction plus rendered-page verification. For PDF papers, report page/region-level issues only; create a comment-annotated DOCX only when the source paper is DOCX.
5. Load `references/format_check_prompt.md` and use it with the rule JSON plus extracted paper evidence.
6. Return JSON following `references/check_output_schema.md`.
7. Run `scripts/build_format_check_outputs.py` to generate:
   - `format_check_analysis.docx`: error statistics and issue table.
   - `annotated_paper.docx`: copy of the original paper with Word comments placed at matching anchor paragraphs.
   - `format_check_result.json`: normalized machine-readable issue data.
8. In the final response, include a short summary and Markdown links to the two user-facing documents.

## Checking Policy

- Check only rules that are present in the provided standard or extracted rule file.
- Do not flag style preferences that are not supported by the standard.
- Each issue must point to a concrete location: section, page, paragraph index, heading/table/figure number, or a short anchor quote.
- Prefer exact evidence from the paper over broad guesses.
- If the model cannot confidently locate an issue in the source document, keep it in the analysis report and set `need_manual_confirmation: true`.
- Group repeated identical violations when appropriate, but keep enough location details for repair.
- Do not overwrite the original paper. Always create a separate annotated copy.
- Use programmatic evidence for Word properties whenever possible. Use the LLM mainly for ambiguous semantic checks, typo/grammar checks, and reasoning over extracted evidence.
- When a rule can be checked deterministically from Word properties, do not rely on visual guessing.
- For font checks, distinguish character scripts. English/Latin text must use Latin font evidence such as `ascii_font` and `hansi_font`; Chinese/CJK text must use `east_asia_font`. A rule requiring English text to use Times New Roman must not be applied to Chinese-only text.

## Output Requirements

The LLM output must be JSON with:

- `paper_analysis`: metadata and check summary.
- `statistics`: total issue counts and breakdowns.
- `issues`: one object per error or grouped error.

Each issue must include:

- `issue_id`, `category`, `severity`, `location`, `rule_id`, `rule_name`
- `expected`, `actual`, `problem`, `reason`, `suggestion`
- `anchor_text` or `location.quote` for comment placement when the source is DOCX
- `comment_text`, `confidence`, `need_manual_confirmation`

`comment_text` must use this fixed structure:

```text
错误原因：...
规范要求：...
修改建议：...
```

If the block above displays as garbled text, ignore it and use this exact structure instead:

```text
错误原因：...
规范要求：...
修改建议：...
```

Use controlled values where possible:

- `category`: `document_structure`, `page_setup`, `font_and_size`, `paragraph_format`, `heading_numbering`, `table_format`, `figure_format`, `citation_reference`, `appendix`, `submission_requirement`, `other_format`
- `severity`: `high`, `medium`, `low`
- `confidence`: `high`, `medium`, `low`

## Build Deliverables

Save raw LLM JSON to a file, then run:

```powershell
python .\skills\check-paper-format\scripts\build_format_check_outputs.py `
  --input .\path\to\format-check-llm-output.json `
  --paper-docx .\sourceFIle\paper.docx `
  --output-dir .\results\format-check `
  --analysis-name format_check_analysis.docx `
  --annotated-name paper_annotated.docx
```

If the source paper is not DOCX, still generate the analysis document and JSON, then state that comment-annotated Word output requires a DOCX source.

## Quality Checks

- Parse the saved JSON before generating documents.
- Verify `issues` exists, even if it is an empty array.
- Confirm the analysis document contains total count, severity breakdown, category breakdown, and issue details.
- For annotated DOCX output, verify the file exists and that comments were placed for issues whose anchor text was found.
- Confirm every inserted Word comment follows `错误原因 + 规范要求 + 修改建议`.
- Confirm the original paper was not overwritten.
- Mention any unplaced comments in the final response; this means the issue is in the report but the exact anchor text was not found in the DOCX.
