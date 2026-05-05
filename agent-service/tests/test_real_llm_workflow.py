import json
import os
import shutil
import subprocess
import sys
import threading
import unittest
import zipfile
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from xml.etree import ElementTree as ET

from app.main import run_request


class FakeDeepSeekHandler(BaseHTTPRequestHandler):
    calls = []

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length).decode("utf-8"))
        self.__class__.calls.append(
            {
                "path": self.path,
                "authorization": self.headers.get("Authorization"),
                "payload": payload,
            }
        )
        if len(self.__class__.calls) == 1:
            content = json.dumps({
                "rules": [
                    {
                        "id": "font.body",
                        "category": "font",
                        "description": "正文必须使用宋体小四。",
                    }
                ]
            }, ensure_ascii=False)
        else:
            content = json.dumps({
                "statistics": {
                    "total_issues": 1,
                    "by_category": {"font": 1},
                    "by_severity": {"medium": 1},
                    "need_manual_confirmation": 0,
                    "checked_rule_count": 1,
                },
                "issues": [
                    {
                        "id": "issue-1",
                        "category": "font",
                        "severity": "medium",
                        "location": "正文第1段",
                        "error_reason": "正文使用了黑体。",
                        "expected_rule": "正文必须使用宋体小四。",
                        "suggestion": "将正文改为宋体小四。",
                    }
                ],
            }, ensure_ascii=False)

        events = [
            {
                "choices": [
                    {
                        "delta": {
                            "reasoning_content": "先识别规范和论文证据。",
                        }
                    }
                ]
            },
            {
                "choices": [
                    {
                        "delta": {
                            "content": content,
                        }
                    }
                ]
            },
        ]
        body = "".join(
            f"data: {json.dumps(event, ensure_ascii=False)}\n\n" for event in events
        ).encode("utf-8") + b"data: [DONE]\n\n"
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


class RealLlmWorkflowTest(unittest.TestCase):
    def setUp(self):
        FakeDeepSeekHandler.calls = []
        self.server = HTTPServer(("127.0.0.1", 0), FakeDeepSeekHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.root = Path(__file__).resolve().parents[1] / "build" / "test-real-llm"
        if self.root.exists():
            shutil.rmtree(self.root)
        self.root.mkdir(parents=True)

    def tearDown(self):
        self.server.shutdown()
        self.thread.join(timeout=5)
        self.server.server_close()

    def write_docx(self, path: Path, text: str) -> None:
        w_ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        ET.register_namespace("w", w_ns)

        def qn(tag: str) -> str:
            return f"{{{w_ns}}}{tag}"

        document = ET.Element(qn("document"))
        body = ET.SubElement(document, qn("body"))
        paragraph = ET.SubElement(body, qn("p"))
        run = ET.SubElement(paragraph, qn("r"))
        text_node = ET.SubElement(run, qn("t"))
        text_node.text = text
        ET.SubElement(body, qn("sectPr"))

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
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as package:
            package.writestr("[Content_Types].xml", content_types)
            package.writestr("_rels/.rels", rels)
            package.writestr("word/document.xml", ET.tostring(document, encoding="utf-8", xml_declaration=True))

    def write_llm_config(self, base_url: str) -> tuple[Path, Path]:
        catalog = self.root / "llm_providers.json"
        local = self.root / "llm_providers.local.json"
        catalog.write_text(
            json.dumps(
                {
                    "providers": [
                        {
                            "id": "deepseek",
                            "display_name": "DeepSeek",
                            "api_protocol": "openai_compatible",
                            "base_url": base_url,
                            "api_key_env": "DEEPSEEK_API_KEY",
                            "models": [{"id": "deepseek-v4-flash"}],
                        }
                    ]
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        local.write_text(
            json.dumps({"providers": {"deepseek": {"api_key": "test-key"}}}, ensure_ascii=False),
            encoding="utf-8",
        )
        return catalog, local

    def test_deepseek_flash_real_mode_calls_chat_completions_and_saves_llm_outputs(self):
        standard = self.root / "simple_standard.md"
        checked = self.root / "simple_paper.docx"
        standard.write_text("# 论文格式规范\n\n正文必须使用宋体小四。", encoding="utf-8")
        self.write_docx(checked, "这是一段黑体正文。")
        catalog, local = self.write_llm_config(f"http://127.0.0.1:{self.server.server_port}")

        result = run_request(
            {
                "provider": "deepseek",
                "model": "deepseek-v4-flash",
                "work_dir": str(self.root / "paper-result"),
                "standard_file": str(standard),
                "checked_file": str(checked),
                "llm_config_path": str(catalog),
                "llm_local_config_path": str(local),
            }
        )

        self.assertTrue(result["success"])
        self.assertEqual(1, result["statistics"]["total_issues"])
        self.assertEqual("issue-1", result["issues"][0]["id"])
        self.assertEqual(3, len(FakeDeepSeekHandler.calls))
        self.assertEqual("/chat/completions", FakeDeepSeekHandler.calls[0]["path"])
        self.assertEqual("Bearer test-key", FakeDeepSeekHandler.calls[0]["authorization"])
        self.assertEqual("deepseek-v4-flash", FakeDeepSeekHandler.calls[0]["payload"]["model"])
        self.assertTrue(FakeDeepSeekHandler.calls[0]["payload"]["stream"])
        self.assertIn(
            "Document Formatting Specification Parsing Expert",
            FakeDeepSeekHandler.calls[0]["payload"]["messages"][0]["content"],
        )
        self.assertIn(
            "复杂格式规则解释 Agent",
            FakeDeepSeekHandler.calls[1]["payload"]["messages"][0]["content"],
        )
        self.assertIn(
            "语言语义拓展检查 Agent",
            FakeDeepSeekHandler.calls[2]["payload"]["messages"][0]["content"],
        )
        self.assertIn(
            "必须使用中文",
            FakeDeepSeekHandler.calls[0]["payload"]["messages"][0]["content"],
        )
        self.assertIn(
            "必须使用中文",
            FakeDeepSeekHandler.calls[1]["payload"]["messages"][0]["content"],
        )

        with open(result["format_rule"]["path"], encoding="utf-8") as rule_file:
            saved_rule = json.load(rule_file)
        with open(self.root / "paper-result" / "检测结果" / "format_check_result.json", encoding="utf-8") as check_file:
            saved_check = json.load(check_file)

        self.assertEqual("font.body", saved_rule["rules"][0]["id"])
        self.assertEqual("issue-1", saved_check["issues"][0]["id"])
        log_path = self.root / "paper-result" / "检测结果" / "llm_interactions.jsonl"
        log_text = log_path.read_text(encoding="utf-8")
        self.assertIn('"event": "prompt"', log_text)
        self.assertIn('"event": "reasoning_delta"', log_text)
        self.assertIn('"event": "answer_delta"', log_text)
        self.assertIn("Document Formatting Specification Parsing Expert", log_text)
        self.assertIn("先识别规范和论文证据。", log_text)

    def test_long_paper_is_sent_to_llm_once_without_dropping_tail(self):
        standard = self.root / "simple_standard.md"
        checked = self.root / "long_paper.docx"
        standard.write_text("# 论文格式规范\n\n正文必须使用宋体小四。", encoding="utf-8")
        long_text = "论文主体内容" * 5000 + "全文最后唯一标记"
        self.write_docx(checked, long_text)
        catalog, local = self.write_llm_config(f"http://127.0.0.1:{self.server.server_port}")

        result = run_request(
            {
                "provider": "deepseek",
                "model": "deepseek-v4-flash",
                "work_dir": str(self.root / "long-paper-result"),
                "standard_file": str(standard),
                "checked_file": str(checked),
                "llm_config_path": str(catalog),
                "llm_local_config_path": str(local),
            }
        )

        self.assertTrue(result["success"])
        self.assertEqual(3, len(FakeDeepSeekHandler.calls))
        language_message = FakeDeepSeekHandler.calls[2]["payload"]["messages"][1]["content"]
        check_messages = [language_message]
        self.assertTrue(any("全文最后唯一标记" in message for message in check_messages))
        self.assertIn("python-docx", language_message)
        self.assertIn("location.paragraph_index", language_message)
        self.assertIn("anchor_text", language_message)
        self.assertTrue(all("全文读取概况 JSON" in message for message in check_messages))
        with open(self.root / "long-paper-result" / "检测结果" / "format_check_result.json", encoding="utf-8") as check_file:
            saved_check = json.load(check_file)
        self.assertEqual(1, saved_check["paper_analysis"]["checked_chunk_count"])
        self.assertEqual(len(long_text), saved_check["paper_analysis"]["checked_text_chars"])
        self.assertEqual(1, saved_check["llm_raw"]["language_semantic"]["chunk_count"])

    def test_cli_prints_stream_log_to_stderr_and_final_json_to_stdout(self):
        standard = self.root / "simple_standard.md"
        checked = self.root / "simple_paper.docx"
        standard.write_text("# 论文格式规范\n\n正文必须使用宋体小四。", encoding="utf-8")
        self.write_docx(checked, "这是一段黑体正文。")
        catalog, local = self.write_llm_config(f"http://127.0.0.1:{self.server.server_port}")
        request_path = self.root / "request.json"
        request_path.write_text(
            json.dumps(
                {
                    "provider": "deepseek",
                    "model": "deepseek-v4-flash",
                    "work_dir": str(self.root / "paper-result-cli"),
                    "standard_file": str(standard),
                    "checked_file": str(checked),
                    "llm_config_path": str(catalog),
                    "llm_local_config_path": str(local),
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

        completed = subprocess.run(
            [sys.executable, "-m", "app.main", "--request", str(request_path)],
            cwd=Path(__file__).resolve().parents[1],
            env={
                **os.environ,
                "PYTHONUTF8": "1",
                "PYTHONIOENCODING": "utf-8",
            },
            capture_output=True,
            timeout=30,
            check=True,
        )

        stdout = completed.stdout.decode("utf-8", errors="replace")
        stderr = completed.stderr.decode("utf-8", errors="replace")
        stdout_payload = json.loads(stdout)
        self.assertTrue(stdout_payload["success"])
        self.assertIn("[agent2_complex_format_explainer] 回答片段", stderr)
        self.assertIn("[agent3_language_semantic_checker] 回答片段", stderr)
        self.assertIn("[agent1_extract_format_rules] 提示词", stderr)
        self.assertIn("[agent1_extract_format_rules] 思考片段", stderr)


if __name__ == "__main__":
    unittest.main()
