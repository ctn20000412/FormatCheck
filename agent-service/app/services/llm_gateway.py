from __future__ import annotations

import json
from typing import Any

import httpx

from app.models.contracts import LlmExecutionContext


class OpenAiCompatibleGateway:
    async def complete_json(
        self,
        context: LlmExecutionContext,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.2,
    ) -> str:
        api_key, api_base_url = context.require_api_settings()
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }

        payload: dict[str, Any] = {
            "model": context.model,
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }

        if context.extra_config:
            payload.update(self._normalize_extra_config(context.extra_config))

        async with httpx.AsyncClient(timeout=httpx.Timeout(180.0, connect=20.0)) as client:
            response = await client.post(
                f"{api_base_url.rstrip('/')}/chat/completions",
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            data = response.json()

        content = (
            data.get("choices", [{}])[0]
            .get("message", {})
            .get("content", "")
        )
        if not content:
            raise ValueError("LLM returned empty content")
        return content

    def _normalize_extra_config(self, extra_config: dict[str, str]) -> dict[str, Any]:
        normalized: dict[str, Any] = {}
        for key, value in extra_config.items():
            if value is None:
                continue
            stripped = value.strip()
            if not stripped:
                continue
            if stripped.lower() in {"true", "false"}:
                normalized[key] = stripped.lower() == "true"
                continue
            try:
                normalized[key] = json.loads(stripped)
            except json.JSONDecodeError:
                normalized[key] = stripped
        return normalized
