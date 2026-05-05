import json
import shutil
import threading
import unittest
import zipfile
import http.client
from contextlib import nullcontext
from http.server import ThreadingHTTPServer
from pathlib import Path

from app.main import (
    AgentHttpHandler,
    build_docx_analysis_evidence,
    build_request_paths,
    check_formula_format_from_docx,
    extract_formulas_from_docx,
    filter_rules_by_check_method,
    normalize_workflow,
    parse_llm_json,
    run_deterministic_checks,
    run_request,
    sanitize_folder_name,
    split_text_chunks,
    write_annotated_copy,
    write_simple_docx,
)


class AgentServiceTest(unittest.TestCase):
    def temp_directory(self):
        temp_root = Path(__file__).resolve().parents[1] / "build" / "test-main-run"
        if temp_root.exists():
            shutil.rmtree(temp_root)
        temp_root.mkdir(parents=True)
        return nullcontext(str(temp_root))

    def test_sanitize_folder_name_keeps_readable_chinese_name(self):
        self.assertEqual(
            sanitize_folder_name("单奕翔：变化中的永恒——论中国民间音乐的“循环”逻辑.docx"),
            "单奕翔_变化中的永恒_论中国民间音乐的_循环_逻辑",
        )

    def test_build_request_paths_uses_business_directories(self):
        paths = build_request_paths(Path("results/善意想"), "paper.docx", "standard.pdf")

        self.assertEqual(paths["checked_file"], Path("results/善意想/待检测文件/paper.docx"))
        self.assertEqual(paths["standard_file"], Path("results/善意想/规则规范文件/standard.pdf"))
        self.assertEqual(paths["format_rule_output"], Path("results/善意想/规则抽取结果/format_rule.json"))
        self.assertEqual(paths["check_result_output"], Path("results/善意想/检测结果/format_check_result.json"))
        self.assertEqual(paths["analysis_output"], Path("results/善意想/检测结果/format_check_analysis.docx"))
        self.assertEqual(paths["annotated_output"], Path("results/善意想/检测后带批注对策源文件/paper_格式检查批注版.docx"))

    def test_split_text_chunks_keeps_tail_content(self):
        text = "\n".join(f"第{i}段内容" for i in range(3000)) + "\n全文最后唯一标记"

        chunks = split_text_chunks(text, max_chars=1000)

        self.assertGreater(len(chunks), 1)
        self.assertIn("第0段内容", chunks[0])
        self.assertIn("全文最后唯一标记", chunks[-1])

    def test_parse_llm_json_repairs_unescaped_quotes_inside_string_values(self):
        content = """{
  "issues": [
    {
      "comment_text": "错误原因：参考文献[3]的刊名中包含了缩写"IJSR"。\\n规范要求：期刊、会议、书籍名称不能用缩写。"
    }
  ]
}"""

        parsed = parse_llm_json(content, "agent2_check_paper_format chunk 4")

        self.assertEqual(
            "错误原因：参考文献[3]的刊名中包含了缩写\"IJSR\"。\n规范要求：期刊、会议、书籍名称不能用缩写。",
            parsed["issues"][0]["comment_text"],
        )

    def test_write_annotated_copy_adds_native_word_comments(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            source = root / "paper.docx"
            target = root / "paper_annotated.docx"
            write_simple_docx(source, ["第一段正常内容", "这里是需要批注的原文锚点"])
            issues = [
                {
                    "issue_id": "I001",
                    "anchor_text": "需要批注的原文锚点",
                    "location": {"paragraph_index": 2, "quote": "需要批注的原文锚点"},
                    "comment_text": "错误原因：测试错误。\n规范要求：测试规范。\n修改建议：测试建议。",
                }
            ]

            write_annotated_copy(source, target, issues)

            with zipfile.ZipFile(target) as package:
                names = set(package.namelist())
                comments_xml = package.read("word/comments.xml").decode("utf-8")
                document_xml = package.read("word/document.xml").decode("utf-8")

            self.assertIn("word/comments.xml", names)
            self.assertIn("错误原因：测试错误。", comments_xml)
            self.assertIn("commentRangeStart", document_xml)

    def test_build_docx_analysis_evidence_includes_wordprocessingml_format_parts(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            source = root / "complex.docx"
            self.write_complex_docx(source)

            evidence = build_docx_analysis_evidence(source)

            self.assertEqual("python-docx+xml", evidence["parser"])
            self.assertIn("word/styles.xml", evidence["package_parts_present"])
            self.assertIn("word/numbering.xml", evidence["package_parts_present"])
            self.assertEqual("Cambria", evidence["styles"]["document_defaults"]["run_properties"]["ascii_font"])
            self.assertEqual("宋体", evidence["styles"]["styles_by_id"]["Normal"]["run_properties"]["east_asia_font"])
            self.assertEqual("1", evidence["numbering"]["paragraph_numbering"][0]["abstract_num_id"])
            self.assertEqual("论文题目", evidence["headers_footers"]["headers"][0]["text"])
            self.assertIn("PAGE", evidence["headers_footers"]["footers"][0]["fields"][0]["instruction"])
            self.assertEqual("脚注内容", evidence["footnotes"][0]["text"])
            self.assertEqual("尾注内容", evidence["endnotes"][0]["text"])
            self.assertEqual("rIdImage1", evidence["images"][0]["relationship_id"])
            self.assertEqual("media/image1.png", evidence["images"][0]["target"])
            self.assertEqual("single", evidence["tables"][0]["borders"]["top"]["val"])
            self.assertEqual("Heading1", evidence["paragraphs"][0]["style_id"])
            self.assertEqual("1", evidence["paragraphs"][0]["numbering"]["num_id"])
            self.assertEqual("宋体", evidence["paragraphs"][1]["runs"][0]["effective_properties"]["east_asia_font"])

    def write_complex_docx(self, path: Path) -> None:
        content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
  <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
  <Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>
  <Override PartName="/word/endnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml"/>
</Types>"""
        rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        document_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdHeader1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
  <Relationship Id="rIdFooter1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
  <Relationship Id="rIdImage1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
  <Relationship Id="rIdFootnotes" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/>
  <Relationship Id="rIdEndnotes" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes" Target="endnotes.xml"/>
</Relationships>"""
        styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Cambria" w:eastAsia="宋体"/><w:sz w:val="24"/></w:rPr></w:rPrDefault></w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="正文"/><w:rPr><w:rFonts w:eastAsia="宋体"/><w:sz w:val="24"/></w:rPr><w:pPr><w:spacing w:line="360" w:lineRule="auto"/></w:pPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="标题 1"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:pPr><w:jc w:val="center"/></w:pPr></w:style>
</w:styles>"""
        numbering = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1"/></w:lvl></w:abstractNum>
  <w:num w:numId="1"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""
        header = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>论文题目</w:t></w:r></w:p></w:hdr>"""
        footer = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p></w:ftr>"""
        footnotes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:footnote w:id="2"><w:p><w:r><w:t>脚注内容</w:t></w:r></w:p></w:footnote></w:footnotes>"""
        endnotes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:endnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:endnote w:id="3"><w:p><w:r><w:t>尾注内容</w:t></w:r></w:p></w:endnote></w:endnotes>"""
        document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>第一章 绪论</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Normal"/></w:pPr><w:r><w:t>正文内容</w:t></w:r><w:r><w:drawing><a:blip r:embed="rIdImage1"/></w:drawing></w:r></w:p>
    <w:tbl><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="12"/><w:bottom w:val="single" w:sz="12"/><w:insideH w:val="single" w:sz="8"/></w:tblBorders></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>表头</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
    <w:sectPr><w:headerReference w:type="default" r:id="rIdHeader1"/><w:footerReference w:type="default" r:id="rIdFooter1"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1417" w:right="1134" w:bottom="1417" w:left="1701" w:header="851" w:footer="851"/></w:sectPr>
  </w:body>
</w:document>"""
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as package:
            package.writestr("[Content_Types].xml", content_types)
            package.writestr("_rels/.rels", rels)
            package.writestr("word/document.xml", document)
            package.writestr("word/_rels/document.xml.rels", document_rels)
            package.writestr("word/styles.xml", styles)
            package.writestr("word/numbering.xml", numbering)
            package.writestr("word/header1.xml", header)
            package.writestr("word/footer1.xml", footer)
            package.writestr("word/footnotes.xml", footnotes)
            package.writestr("word/endnotes.xml", endnotes)
            package.writestr("word/media/image1.png", b"\x89PNG\r\n\x1a\n")

    def test_run_request_generates_expected_outputs(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            checked = root / "paper.docx"
            standard = root / "standard.pdf"
            checked.write_bytes(b"fake docx")
            standard.write_bytes(b"fake pdf")
            catalog = root / "llm_providers.json"
            local = root / "llm_providers.local.json"
            catalog.write_text(
                json.dumps(
                    {
                        "providers": [
                            {
                                "id": "deepseek",
                                "base_url": "https://api.deepseek.com",
                                "api_key_env": "TEST_DEEPSEEK_API_KEY",
                                "models": [{"id": "deepseek-chat"}],
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            local.write_text(json.dumps({"providers": {}}, ensure_ascii=False), encoding="utf-8")
            request = {
                "provider": "deepseek",
                "model": "deepseek-chat",
                "workflow": "full_check",
                "work_dir": str(root / "善意想"),
                "checked_file": str(checked),
                "standard_file": str(standard),
                "llm_config_path": str(catalog),
                "llm_local_config_path": str(local),
                "check_result_output": str(root / "explicit-check-result.json"),
            }

            result = run_request(request)

            self.assertTrue(result["success"])
            self.assertEqual("full_check", result["workflow"])
            self.assertTrue(Path(result["format_rule"]["path"]).exists())
            self.assertTrue(Path(result["analysis_doc"]["path"]).exists())
            self.assertTrue(Path(result["annotated_doc"]["path"]).exists())
            self.assertEqual(result["statistics"]["total_issues"], 0)
            with open(result["format_rule"]["path"], encoding="utf-8") as f:
                self.assertIn("rules", json.load(f))
            with open(root / "explicit-check-result.json", encoding="utf-8") as f:
                self.assertEqual("full_check", json.load(f)["workflow"])

    def test_compare_workflow_returns_comparison_without_configured_api_key(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            checked = root / "paper.docx"
            standard = root / "standard.pdf"
            checked.write_bytes(b"fake docx")
            standard.write_bytes(b"fake pdf")
            catalog = root / "llm_providers.json"
            local = root / "llm_providers.local.json"
            catalog.write_text(
                json.dumps(
                    {
                        "providers": [
                            {
                                "id": "deepseek",
                                "base_url": "https://api.deepseek.com",
                                "api_key_env": "TEST_DEEPSEEK_API_KEY",
                                "models": [{"id": "deepseek-chat"}],
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            local.write_text(json.dumps({"providers": {}}, ensure_ascii=False), encoding="utf-8")
            request = {
                "provider": "deepseek",
                "model": "deepseek-chat",
                "workflow": "full_check",
                "work_dir": str(root / "compare-result"),
                "checked_file": str(checked),
                "standard_file": str(standard),
                "llm_config_path": str(catalog),
                "llm_local_config_path": str(local),
                "check_result_output": str(root / "explicit-compare-result.json"),
            }

            result = run_request(request)

            self.assertTrue(result["success"])
            self.assertEqual("full_check", result["workflow"])
            with open(root / "explicit-compare-result.json", encoding="utf-8") as f:
                self.assertEqual("full_check", json.load(f)["workflow"])

    def test_english_font_rule_ignores_chinese_runs(self):
        rule = {
            "rule_id": "R001",
            "category": "Font and Font Size",
            "requirement": "英文文本使用 Times New Roman。",
            "expected_value": "Times New Roman",
            "check_method": "deterministic",
        }
        evidence = {
            "paragraphs": [
                {
                    "index": 1,
                    "text": "中文标题",
                    "runs": [{"text": "中文标题", "effective_properties": {"east_asia_font": "宋体"}}],
                },
                {
                    "index": 2,
                    "text": "English title",
                    "runs": [{"text": "English title", "effective_properties": {"ascii_font": "Calibri"}}],
                },
            ]
        }

        issues = run_deterministic_checks([rule], evidence)

        self.assertEqual(1, len(issues))
        self.assertEqual(2, issues[0]["location"]["paragraph_index"])

    def test_english_font_rule_uses_hansi_fallback(self):
        rule = {
            "rule_id": "R002",
            "category": "Font and Font Size",
            "requirement": "\u82f1\u6587\u6587\u672c\u4f7f\u7528 Times New Roman\u3002",
            "expected_value": "Times New Roman",
            "check_method": "deterministic",
        }
        evidence = {
            "paragraphs": [
                {
                    "index": 3,
                    "text": "English subtitle",
                    "runs": [{"text": "English subtitle", "effective_properties": {"hansi_font": "Calibri"}}],
                }
            ]
        }

        issues = run_deterministic_checks([rule], evidence)

        self.assertEqual(1, len(issues))
        self.assertEqual(3, issues[0]["location"]["paragraph_index"])

    def test_new_workflow_names_are_supported_and_old_names_are_aliased(self):
        self.assertEqual("full_check", normalize_workflow(None))
        self.assertEqual("hard_format", normalize_workflow("hard_format"))
        self.assertEqual("semantic_llm", normalize_workflow("semantic_llm"))
        self.assertEqual("full_check", normalize_workflow("full_check"))
        self.assertEqual("semantic_llm", normalize_workflow("llm_direct"))
        self.assertEqual("full_check", normalize_workflow("hybrid"))
        self.assertEqual("full_check", normalize_workflow("compare"))

    def test_rules_are_split_between_python_and_llm_checkers(self):
        rules = [
            {"rule_id": "R001", "description": "正文中文使用宋体", "check_method": "python"},
            {"rule_id": "R002", "description": "英文摘要与中文摘要语义对应", "check_method": "llm"},
            {"rule_id": "R003", "description": "参考文献引用关系应一致", "check_method": "llm_semantic"},
            {"rule_id": "R004", "description": "页边距为上 3 cm", "check_method": "deterministic"},
        ]

        self.assertEqual(["R001", "R004"], [rule["rule_id"] for rule in filter_rules_by_check_method(rules, "python")])
        self.assertEqual(["R002", "R003"], [rule["rule_id"] for rule in filter_rules_by_check_method(rules, "llm")])

    def test_formula_ooxml_is_extracted_and_missing_number_is_reported(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            source = root / "formula.docx"
            self.write_formula_docx(source)
            rule = {
                "rule_id": "R-FORMULA-001",
                "category": "formula_format",
                "description": "公式必须编号",
                "check_method": "python",
            }

            formulas = extract_formulas_from_docx(source)
            issues = check_formula_format_from_docx(source, [rule])

            self.assertEqual(1, len(formulas))
            self.assertEqual(1, formulas[0]["paragraph_index"])
            self.assertEqual(1, len(issues))
            self.assertEqual("formula_format", issues[0]["category"])
            self.assertEqual(1, issues[0]["location"]["paragraph_index"])

    def write_formula_docx(self, path: Path) -> None:
        content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
  <w:body>
    <w:p>
      <w:r><w:t>E = mc</w:t></w:r>
      <m:oMath><m:r><m:t>2</m:t></m:r></m:oMath>
    </w:p>
    <w:sectPr/>
  </w:body>
</w:document>"""
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as package:
            package.writestr("[Content_Types].xml", content_types)
            package.writestr("_rels/.rels", rels)
            package.writestr("word/document.xml", document)

    def test_http_agent_check_endpoint_runs_request(self):
        with self.temp_directory() as temp:
            root = Path(temp)
            checked = root / "paper.docx"
            standard = root / "standard.pdf"
            checked.write_bytes(b"fake docx")
            standard.write_bytes(b"fake pdf")
            catalog = root / "llm_providers.json"
            local = root / "llm_providers.local.json"
            catalog.write_text(
                json.dumps(
                    {
                        "providers": [
                            {
                                "id": "deepseek",
                                "base_url": "https://api.deepseek.com",
                                "api_key_env": "TEST_DEEPSEEK_API_KEY",
                                "models": [{"id": "deepseek-chat"}],
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            local.write_text(json.dumps({"providers": {}}, ensure_ascii=False), encoding="utf-8")
            server = ThreadingHTTPServer(("127.0.0.1", 0), AgentHttpHandler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            connection = None
            try:
                payload = {
                    "provider": "deepseek",
                    "model": "deepseek-chat",
                    "work_dir": str(root / "paper-result-http"),
                    "checked_file": str(checked),
                    "standard_file": str(standard),
                    "llm_config_path": str(catalog),
                    "llm_local_config_path": str(local),
                }
                body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=10)
                connection.request("POST", "/agent/check", body=body, headers={"Content-Type": "application/json"})
                response = connection.getresponse()
                response_payload = json.loads(response.read().decode("utf-8"))
            finally:
                if connection is not None:
                    connection.close()
                server.shutdown()
                thread.join(timeout=5)
                server.server_close()

            self.assertEqual(200, response.status)
            self.assertTrue(response_payload["success"])
            self.assertTrue(Path(response_payload["format_rule"]["path"]).exists())


if __name__ == "__main__":
    unittest.main()
