# Document IO Methods For Rule Extraction

Use this reference before invoking the LLM. Build a compact evidence package from the source file first, then pass that package to `extract_rule_prompt.md`.

## Parser Selection

| Input | Preferred method | Fallback | Notes |
| --- | --- | --- | --- |
| `.docx` | `python-docx` + direct WordprocessingML inspection | text-only extraction | Use both visible text and formatting XML. |
| `.doc` | LibreOffice headless conversion to `.docx`, then DOCX method | ask for `.docx` if conversion fails | Preserve original `.doc`; record conversion in `selected_parser`. |
| `.pdf` | `pdfplumber` text/tables + `pypdf` metadata + Poppler render check | OCR only for scanned/sparse text | Do not trust text extraction alone for layout-heavy PDFs. |
| `.md` | direct UTF-8 read with heading/list/table preservation | detect encoding and retry | Preserve line numbers and heading hierarchy. |
| `.txt` | direct text read | detect encoding and retry | Keep paragraph breaks. |

## DOCX Evidence Method

Use `python-docx` for high-level content:

- Paragraph text, paragraph index, style name.
- Heading text and level when style names identify headings.
- Tables, rows, cells, captions near tables.
- Section objects when available.

Use direct OOXML inspection with `zipfile` and `xml.etree.ElementTree` or `lxml` for properties that `python-docx` may not expose:

- `word/document.xml`: `w:sectPr`, `w:pgSz`, `w:pgMar`, paragraph `w:pPr`, run `w:rPr`.
- `word/styles.xml`: default styles, named styles, based-on inheritance, fonts, size, bold, alignment, spacing.
- `word/numbering.xml`: numbering definitions and heading/list numbering.
- `word/header*.xml` and `word/footer*.xml`: headers, footers, page number fields, school/template marks.
- `docProps/core.xml` and `docProps/app.xml`: source metadata only; do not treat metadata as formatting rules.

Normalize common Word values:

- Twips to points: `pt = twips / 20`.
- Twips to millimeters: `mm = twips * 25.4 / 1440`.
- Half-points: `pt = value / 2`.
- Line spacing: preserve exact Word value and provide a human-readable value when clear.

When a DOCX is a template rather than prose instructions, infer rules from repeated style definitions and mark those rules:

```json
{
  "source_type": "template_example",
  "need_manual_confirmation": true,
  "confidence": "medium"
}
```

## DOC Conversion Method

If the input is `.doc`, convert it before extracting:

```powershell
soffice --headless --convert-to docx --outdir <temp-dir> <input.doc>
```

If LibreOffice is unavailable or conversion fails, report that `.doc` parsing is blocked and ask for a `.docx` or `.pdf` version. Do not silently ignore the file.

## PDF Evidence Method

Use layered extraction:

1. Use `pdfplumber` to extract page text, tables, approximate bounding boxes, and page dimensions.
2. Use `pypdf` for metadata and page count.
3. Render pages with Poppler for visual verification:

```powershell
pdftoppm -png <input.pdf> <output-prefix>
```

If extracted text is sparse, garbled, or obviously missing:

- Try OCR if an OCR engine is available.
- Otherwise stop and report that the PDF appears scanned or extraction quality is too low.

PDF font size, line spacing, and margins may be approximate. Mark inferred layout rules as low or medium confidence unless the requirement is explicitly stated in text.

## Markdown/Text Evidence Method

Preserve:

- Heading levels.
- Ordered and unordered lists.
- Tables.
- Code blocks only when they describe required file names or formatting examples.
- Source line numbers for evidence.

## Evidence Package Shape

Build a compact package like this before prompting:

```json
{
  "source_file": {
    "path": "string",
    "file_name": "string",
    "file_type": "docx|doc|pdf|md|txt",
    "selected_parser": "string"
  },
  "extraction_quality": {
    "text_complete": true,
    "layout_verified": true,
    "warnings": []
  },
  "text_blocks": [
    {
      "block_id": "B001",
      "page": 1,
      "paragraph_index": 1,
      "style": "Heading 1",
      "text": "string"
    }
  ],
  "tables": [],
  "styles": [],
  "page_setup": [],
  "headers_footers": []
}
```

Only include evidence that helps rule extraction. Keep long body text trimmed to relevant standard clauses, tables, and template examples.

## Quality Gates

- Do not send a binary file directly to the LLM without an extracted evidence package.
- Do not invent missing values from visual appearance alone.
- Stop when extracted text is mojibake, empty, or clearly incomplete.
- Record parser warnings in `file_analysis.analysis_summary`.
