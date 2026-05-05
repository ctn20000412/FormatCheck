from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


CONFIG_DIR = Path(__file__).resolve().parents[1] / "config"
DEFAULT_CONFIG_PATH = CONFIG_DIR / "llm_providers.json"
DEFAULT_LOCAL_CONFIG_PATH = CONFIG_DIR / "llm_providers.local.json"


@dataclass(frozen=True)
class LlmConfigStore:
    catalog_path: Path = DEFAULT_CONFIG_PATH
    local_path: Path = DEFAULT_LOCAL_CONFIG_PATH


def _read_json(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    if not path.exists():
        return default
    with open(path, encoding="utf-8") as config_file:
        return json.load(config_file)


def _write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _normalize_models(provider: dict[str, Any]) -> list[dict[str, str]]:
    models = []
    for model in provider.get("models", []):
        if isinstance(model, str):
            models.append({"id": model, "display_name": model})
        elif isinstance(model, dict) and model.get("id"):
            models.append(
                {
                    "id": str(model["id"]),
                    "display_name": str(model.get("display_name") or model["id"]),
                }
            )
    return models


def _provider_map(store: LlmConfigStore) -> dict[str, dict[str, Any]]:
    catalog = _read_json(store.catalog_path, {"providers": []})
    providers: dict[str, dict[str, Any]] = {}
    for provider in catalog.get("providers", []):
        if isinstance(provider, dict) and provider.get("id"):
            normalized = dict(provider)
            normalized["models"] = _normalize_models(provider)
            providers[str(provider["id"])] = normalized
    return providers


def _local_provider_overrides(store: LlmConfigStore) -> dict[str, dict[str, Any]]:
    local = _read_json(store.local_path, {"providers": {}})
    providers = local.get("providers", {})
    return providers if isinstance(providers, dict) else {}


def list_model_options(store: LlmConfigStore | None = None) -> list[dict[str, str]]:
    store = store or LlmConfigStore()
    options = []
    for provider_id, provider in _provider_map(store).items():
        for model in provider["models"]:
            options.append({"provider": provider_id, "model": model["id"]})
    return options


def load_provider_settings(
    provider_id: str,
    model_id: str | None = None,
    store: LlmConfigStore | None = None,
) -> dict[str, Any]:
    store = store or LlmConfigStore()
    providers = _provider_map(store)
    if provider_id not in providers:
        raise ValueError(f"Unknown model provider: {provider_id}")

    provider = dict(providers[provider_id])
    overrides = _local_provider_overrides(store).get(provider_id, {})
    if isinstance(overrides, dict):
        for key in ("base_url", "api_key_env", "api_key"):
            if overrides.get(key):
                provider[key] = overrides[key]

    models = provider.get("models", [])
    valid_models = {model["id"] for model in models}
    selected_model = model_id or (models[0]["id"] if models else "")
    if not selected_model:
        raise ValueError(f"No models configured for provider: {provider_id}")
    if selected_model not in valid_models:
        raise ValueError(f"Unsupported model for {provider_id}: {selected_model}")

    api_key_env = str(provider.get("api_key_env") or "")
    api_key = str(provider.get("api_key") or "")
    if not api_key and api_key_env:
        api_key = os.environ.get(api_key_env, "")

    return {
        "provider": provider_id,
        "provider_name": provider.get("display_name") or provider_id,
        "model": selected_model,
        "api_protocol": provider.get("api_protocol") or "openai_compatible",
        "base_url": provider.get("base_url") or "",
        "api_key_env": api_key_env,
        "api_key": api_key,
        "api_key_configured": bool(api_key),
    }


def _update_local_provider(provider_id: str, values: dict[str, str], store: LlmConfigStore) -> dict[str, Any]:
    if provider_id not in _provider_map(store):
        raise ValueError(f"Unknown model provider: {provider_id}")
    local = _read_json(store.local_path, {"providers": {}})
    providers = local.setdefault("providers", {})
    provider = providers.setdefault(provider_id, {})
    provider.update(values)
    _write_json(store.local_path, local)
    return load_provider_settings(provider_id, None, store)


def set_base_url(provider_id: str, base_url: str, store: LlmConfigStore | None = None) -> dict[str, Any]:
    return _update_local_provider(provider_id, {"base_url": base_url}, store or LlmConfigStore())


def set_api_key_env(provider_id: str, api_key_env: str, store: LlmConfigStore | None = None) -> dict[str, Any]:
    return _update_local_provider(provider_id, {"api_key_env": api_key_env}, store or LlmConfigStore())


def set_api_key(provider_id: str, api_key: str, store: LlmConfigStore | None = None) -> dict[str, Any]:
    return _update_local_provider(provider_id, {"api_key": api_key}, store or LlmConfigStore())


def _public_settings(settings: dict[str, Any]) -> dict[str, Any]:
    public = dict(settings)
    public.pop("api_key", None)
    return public


def _print_json(data: Any) -> None:
    print(json.dumps(data, ensure_ascii=False, indent=2))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Manage local LLM provider settings.")
    parser.add_argument("--catalog", default=str(DEFAULT_CONFIG_PATH), help="Provider catalog JSON path.")
    parser.add_argument("--local", default=str(DEFAULT_LOCAL_CONFIG_PATH), help="Local override JSON path.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("list", help="List providers and models.")

    show_parser = subparsers.add_parser("show", help="Show resolved provider settings without printing the key.")
    show_parser.add_argument("provider")
    show_parser.add_argument("--model")

    set_url_parser = subparsers.add_parser("set-url", help="Override a provider base URL locally.")
    set_url_parser.add_argument("provider")
    set_url_parser.add_argument("base_url")

    set_key_env_parser = subparsers.add_parser("set-api-key-env", help="Override a provider API key env name locally.")
    set_key_env_parser.add_argument("provider")
    set_key_env_parser.add_argument("api_key_env")

    set_key_parser = subparsers.add_parser("set-api-key", help="Store a provider API key in the ignored local file.")
    set_key_parser.add_argument("provider")
    set_key_parser.add_argument("api_key")

    args = parser.parse_args(argv)
    store = LlmConfigStore(Path(args.catalog), Path(args.local))

    if args.command == "list":
        _print_json(list_model_options(store))
    elif args.command == "show":
        _print_json(_public_settings(load_provider_settings(args.provider, args.model, store)))
    elif args.command == "set-url":
        _print_json(_public_settings(set_base_url(args.provider, args.base_url, store)))
    elif args.command == "set-api-key-env":
        _print_json(_public_settings(set_api_key_env(args.provider, args.api_key_env, store)))
    elif args.command == "set-api-key":
        _print_json(_public_settings(set_api_key(args.provider, args.api_key, store)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
