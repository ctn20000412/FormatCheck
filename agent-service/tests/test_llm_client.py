import io
import unittest
from contextlib import redirect_stderr

from app import llm_client


class LlmClientTerminalLogTest(unittest.TestCase):
    def setUp(self):
        llm_client._terminal_stream_key = None

    def tearDown(self):
        llm_client._terminal_stream_key = None

    def test_answer_delta_appends_chunks_after_one_prefix(self):
        stderr = io.StringIO()

        with redirect_stderr(stderr):
            llm_client.write_terminal_log(
                {"agent": "agent2_check_paper_format", "event": "answer_delta", "content": "引用"}
            )
            llm_client.write_terminal_log(
                {"agent": "agent2_check_paper_format", "event": "answer_delta", "content": "序号"}
            )
            llm_client.write_terminal_log(
                {"agent": "agent2_check_paper_format", "event": "answer_delta", "content": "（如[1"}
            )
            llm_client.write_terminal_log({"agent": "agent2_check_paper_format", "event": "final"})

        output = stderr.getvalue()
        self.assertIn("[agent2_check_paper_format] 回答片段：引用序号（如[1", output)
        self.assertEqual(1, output.count("[agent2_check_paper_format] 回答片段："))
        self.assertIn("\n[agent2_check_paper_format] 完成", output)

    def test_switching_from_reasoning_to_answer_starts_new_line(self):
        stderr = io.StringIO()

        with redirect_stderr(stderr):
            llm_client.write_terminal_log(
                {"agent": "agent1_extract_format_rules", "event": "reasoning_delta", "content": "先分析"}
            )
            llm_client.write_terminal_log(
                {"agent": "agent1_extract_format_rules", "event": "answer_delta", "content": "规则"}
            )

        output = stderr.getvalue()
        self.assertIn("[agent1_extract_format_rules] 思考片段：先分析\n", output)
        self.assertIn("[agent1_extract_format_rules] 回答片段：规则", output)


if __name__ == "__main__":
    unittest.main()
