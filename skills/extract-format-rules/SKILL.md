---
name: extract-format-rules
description: Use when a user uploads or references a PDF, Word, Markdown, or text file containing academic or institutional document-format requirements and needs the formatting, layout, typography, pagination, numbering, citation, table, figure, or appendix rules extracted into a structured JSON rules file for display and download.
---

# Extract Format Rules

## Overview

Extract formatting requirements from standard/specification documents into machine-readable JSON. Use this skill for `.pdf`, `.doc`, `.docx`, `.md`, `.txt`, and similar files that describe document layout, typography, pagination, headings, tables, figures, citations, appendices, or submission requirements.

## Workflow

1. Identify the uploaded or referenced standard file and preserve its original filename.
2. Read `references/document_io_methods.md`, then extract a document-evidence package with the best available parser for the file type.
3. For DOCX/DOC/PDF, use mature document-processing methods before asking the LLM to reason:
   - DOCX: use `python-docx` for text/tables and WordprocessingML for styles, page setup, headers, footers, numbering, and inherited formatting.
   - DOC: convert to DOCX with LibreOffice headless first when available, then process as DOCX.
   - PDF: use `pdfplumber`/`pypdf` for text and metadata, render pages with Poppler for layout verification, and use OCR only when text extraction is sparse.
4. Load `references/extract_rule_prompt.md` and apply it to the extracted evidence package, not only raw text.
5. Produce JSON only, following `references/rule_json_schema.md`.
6. Validate and save the JSON with `scripts/save_rule_json.py`.
7. In the final response, show a concise summary, a short preview of representative rules, the JSON file path, and a Markdown download link.

## Extraction Rules

- Extract only requirements about document format, layout, typesetting, structure, and presentation.
- Include both explicit rules and template-example rules, but mark template-derived rules with `source_type: "template_example"`.
- Do not invent values. If a requirement is implied or ambiguous, keep the rule, set `need_manual_confirmation: true`, and lower `confidence`.
- Split compound requirements into atomic rules when they can be checked independently, such as top margin, bottom margin, font family, font size, and line spacing.
- Keep source evidence short and directly tied to the rule.
- Prefer normalized values: use `mm`, `pt`, Chinese font-size names, page-size names, or exact strings from the source.
- Use stable sequential IDs: `R001`, `R002`, `R003`.

## Output Contract

Return an object with:

- `file_analysis`: source file metadata, parser choice, and extraction summary.
- `rules`: an array of rule objects.

Each rule must include `rule_id`, `category`, `rule_name`, `scope`, `requirement`, `check_logic`, `expected_value`, `source_text`, `source_type`, `confidence`, `need_manual_confirmation`, and `severity`.

Use these controlled values where possible:

- `category`: `document_structure`, `page_setup`, `font_and_size`, `paragraph_format`, `heading_numbering`, `table_format`, `figure_format`, `citation_reference`, `appendix`, `submission_requirement`, `other_format`
- `source_type`: `explicit_text`, `template_example`, `inferred_from_context`
- `confidence`: `high`, `medium`, `low`
- `severity`: `high`, `medium`, `low`

## Saving And Links

After generating or receiving the JSON from the LLM, save it:

```powershell
python .\skills\extract-format-rules\scripts\save_rule_json.py `
  --input .\path\to\llm-output.json `
  --output-dir .\results\format-rules `
  --source-file .\sourceFIle\standard.docx
```

The script accepts raw LLM output that contains either plain JSON or JSON inside a Markdown code fence. It writes a normalized `.json` file and prints its absolute path. Use that path as the download link target:

```markdown
[下载格式规范 JSON](E:\code\formatCheck\results\format-rules\standard_format_rules.json)
```

If the environment is a web app, also expose the saved file through the app's existing download endpoint and include that URL.

## Quality Checks

- Parse the saved JSON again before claiming completion.
- Verify `rules` is not empty.
- Check that every rule has source evidence and a checkable expected value.
- Confirm ambiguous or template-derived rules are flagged for manual confirmation.
- Confirm `file_analysis.selected_parser` names the actual parser path, such as `docx-python-docx-ooxml`, `doc-libreoffice-docx-ooxml`, `pdf-pdfplumber-poppler`, or `markdown-direct`.
- For DOCX/DOC template files, verify style-derived rules are marked `source_type: "template_example"` unless the rule is explicitly stated in text.
- For PDF files, prefer rules backed by text evidence; mark font-size or spacing inferred only from rendered layout as `need_manual_confirmation: true`.
- If the extracted text is visibly mojibake or incomplete, stop and report the parsing issue instead of producing unreliable rules.
