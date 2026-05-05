from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib import error, request


_terminal_stream_key: tuple[str, str] | None = None


def write_jsonl(log_path: Path | None, event: dict[str, Any]) -> None:
    if log_path is None:
        return
    log_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        **event,
    }
    with open(log_path, "a", encoding="utf-8") as log_file:
        log_file.write(json.dumps(payload, ensure_ascii=False) + "\n")


def close_terminal_stream() -> None:
    global _terminal_stream_key
    if _terminal_stream_key is not None:
        print(file=sys.stderr, flush=True)
        _terminal_stream_key = None


def write_terminal_delta(agent: str, event_name: str, label: str, content: str) -> None:
    global _terminal_stream_key
    stream_key = (agent, event_name)
    if _terminal_stream_key != stream_key:
        close_terminal_stream()
        print(f"[{agent}] {label}：", end="", file=sys.stderr, flush=True)
        _terminal_stream_key = stream_key
    print(content, end="", file=sys.stderr, flush=True)


def write_terminal_log(event: dict[str, Any]) -> None:
    agent = event.get("agent") or "llm"
    event_name = event.get("event") or "event"
    if event_name == "prompt":
        close_terminal_stream()
        message_count = len(event.get("messages") or [])
        print(f"[{agent}] 提示词 messages={message_count}", file=sys.stderr, flush=True)
        for index, message in enumerate(event.get("messages") or [], start=1):
            role = message.get("role", "")
            content = str(message.get("content", ""))
            print(f"[{agent}] 提示词[{index}] role={role}\n{content}", file=sys.stderr, flush=True)
        return
    if event_name == "reasoning_delta":
        write_terminal_delta(agent, event_name, "思考片段", str(event.get("content", "")))
        return
    if event_name == "answer_delta":
        write_terminal_delta(agent, event_name, "回答片段", str(event.get("content", "")))
        return
    if event_name == "final":
        close_terminal_stream()
        print(f"[{agent}] 完成", file=sys.stderr, flush=True)


def call_openai_compatible_chat(
    settings: dict[str, Any],
    messages: list[dict[str, str]],
    *,
    agent_name: str | None = None,
    log_path: Path | None = None,
) -> str:
    api_key = settings.get("api_key") or ""
    if not api_key:
        raise RuntimeError(f"API key is not configured for provider: {settings.get('provider')}")

    base_url = str(settings.get("base_url") or "").rstrip("/")
    if not base_url:
        raise RuntimeError(f"Base URL is not configured for provider: {settings.get('provider')}")

    payload = {
        "model": settings["model"],
        "messages": messages,
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "stream": True,
    }
    prompt_event = {
        "agent": agent_name,
        "event": "prompt",
        "provider": settings.get("provider"),
        "model": settings.get("model"),
        "messages": messages,
    }
    write_jsonl(log_path, prompt_event)
    write_terminal_log(prompt_event)
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    http_request = request.Request(
        f"{base_url}/chat/completions",
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with request.urlopen(http_request, timeout=120) as response:
            answer_parts: list[str] = []
            reasoning_parts: list[str] = []
            for raw_line in response:
                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line or not line.startswith("data:"):
                    continue
                data = line.removeprefix("data:").strip()
                if data == "[DONE]":
                    break
                event = json.loads(data)
                choices = event.get("choices") or []
                if not choices:
                    continue
                delta = choices[0].get("delta") or {}
                reasoning = delta.get("reasoning_content") or delta.get("reasoning")
                answer = delta.get("content")
                if reasoning:
                    reasoning_parts.append(str(reasoning))
                    event_payload = {
                        "agent": agent_name,
                        "event": "reasoning_delta",
                        "content": str(reasoning),
                    }
                    write_jsonl(log_path, event_payload)
                    write_terminal_log(event_payload)
                if answer:
                    answer_parts.append(str(answer))
                    event_payload = {
                        "agent": agent_name,
                        "event": "answer_delta",
                        "content": str(answer),
                    }
                    write_jsonl(log_path, event_payload)
                    write_terminal_log(event_payload)
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API returned HTTP {exc.code}: {detail}") from exc

    content = "".join(answer_parts)
    if not content:
        raise RuntimeError("LLM API returned empty message content")
    final_event = {
        "agent": agent_name,
        "event": "final",
        "reasoning": "".join(reasoning_parts),
        "answer": content,
    }
    write_jsonl(log_path, final_event)
    write_terminal_log(final_event)
    return str(content)
