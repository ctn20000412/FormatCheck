import json
import os
import unittest
import uuid
from pathlib import Path

from app.llm_config import (
    LlmConfigStore,
    list_model_options,
    load_provider_settings,
    set_api_key,
    set_api_key_env,
    set_base_url,
)


class LlmConfigTest(unittest.TestCase):
    def temp_root(self) -> Path:
        root = Path(__file__).resolve().parents[1] / "build" / "test-llm-config" / uuid.uuid4().hex
        root.mkdir(parents=True, exist_ok=True)
        return root

    def write_json(self, path: Path, data: dict) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    def test_lists_models_from_provider_catalog(self):
        root = self.temp_root()
        base = root / "llm_providers.json"
        local = root / "llm_providers.local.json"
        self.write_json(
            base,
            {
                "providers": [
                    {
                        "id": "deepseek",
                        "display_name": "DeepSeek",
                        "base_url": "https://api.deepseek.com",
                        "api_key_env": "DEEPSEEK_API_KEY",
                        "api_protocol": "openai_compatible",
                        "models": [{"id": "deepseek-v4-flash"}, {"id": "deepseek-v4-pro"}],
                    }
                ]
            },
        )

        options = list_model_options(LlmConfigStore(base, local))

        self.assertEqual(
            options,
            [
                {"provider": "deepseek", "model": "deepseek-v4-flash"},
                {"provider": "deepseek", "model": "deepseek-v4-pro"},
            ],
        )

    def test_local_settings_override_url_and_key_without_changing_catalog(self):
        root = self.temp_root()
        base = root / "llm_providers.json"
        local = root / "llm_providers.local.json"
        self.write_json(
            base,
            {
                "providers": [
                    {
                        "id": "glm",
                        "display_name": "GLM",
                        "base_url": "https://api.z.ai/api/paas/v4",
                        "api_key_env": "ZAI_API_KEY",
                        "api_protocol": "openai_compatible",
                        "models": [{"id": "glm-5.1"}],
                    }
                ]
            },
        )
        store = LlmConfigStore(base, local)

        set_base_url("glm", "https://example.test/v1", store)
        set_api_key_env("glm", "CUSTOM_GLM_KEY", store)
        set_api_key("glm", "secret-value", store)
        settings = load_provider_settings("glm", "glm-5.1", store)

        self.assertEqual(settings["base_url"], "https://example.test/v1")
        self.assertEqual(settings["api_key_env"], "CUSTOM_GLM_KEY")
        self.assertEqual(settings["api_key"], "secret-value")
        self.assertTrue(settings["api_key_configured"])
        self.assertEqual(
            json.loads(base.read_text(encoding="utf-8"))["providers"][0]["base_url"],
            "https://api.z.ai/api/paas/v4",
        )

    def test_environment_key_is_used_when_local_key_is_absent(self):
        root = self.temp_root()
        base = root / "llm_providers.json"
        local = root / "llm_providers.local.json"
        self.write_json(
            base,
            {
                "providers": [
                    {
                        "id": "chatgpt",
                        "display_name": "ChatGPT",
                        "base_url": "https://api.openai.com/v1",
                        "api_key_env": "OPENAI_API_KEY",
                        "api_protocol": "openai",
                        "models": [{"id": "gpt-5.5"}],
                    }
                ]
            },
        )
        old_value = os.environ.get("OPENAI_API_KEY")
        os.environ["OPENAI_API_KEY"] = "env-secret"
        try:
            settings = load_provider_settings("chatgpt", "gpt-5.5", LlmConfigStore(base, local))
        finally:
            if old_value is None:
                os.environ.pop("OPENAI_API_KEY", None)
            else:
                os.environ["OPENAI_API_KEY"] = old_value

        self.assertEqual(settings["api_key"], "env-secret")
        self.assertTrue(settings["api_key_configured"])


if __name__ == "__main__":
    unittest.main()
