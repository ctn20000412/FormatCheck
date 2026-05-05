import unittest
import os

from app.llm_client import call_openai_compatible_chat
from app.llm_config import load_provider_settings


class LiveDeepSeekApiTest(unittest.TestCase):
    def test_deepseek_v4_flash_chat_completions_returns_json(self):
        if os.environ.get("RUN_LIVE_DEEPSEEK_API") != "1":
            self.skipTest("Set RUN_LIVE_DEEPSEEK_API=1 to call the live DeepSeek API.")
        settings = load_provider_settings("deepseek", "deepseek-v4-flash")
        if not settings["api_key_configured"]:
            self.skipTest("DeepSeek API key is not configured.")

        content = call_openai_compatible_chat(
            settings,
            [
                {
                    "role": "system",
                    "content": "只返回 JSON。",
                },
                {
                    "role": "user",
                    "content": "返回 {\"ok\": true}。",
                },
            ],
        )

        self.assertIn("ok", content)


if __name__ == "__main__":
    unittest.main()
