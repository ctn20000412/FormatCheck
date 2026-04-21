# agent-service

Python Agent 服务，负责：

- 规则抽取：`POST /internal/agent/rules/extract`
- 格式检查：`POST /internal/agent/checks/execute`
- 健康检查：`GET /health`

## 安装

```bash
pip install -r requirements.txt
```

## 启动

```bash
uvicorn app.main:app --host 0.0.0.0 --port 9000
```

## 与后端联调

将后端 [application.yml](</E:/code/formatCheck/backend/src/main/resources/application.yml>) 中的：

```yaml
app:
  agent:
    mode: http
    base-url: http://localhost:9000
```

并配置至少一个提供商的 `api-key`。

## 提供商切换

后端调用 `/api/rules/extract` 或 `/api/checks` 时可传：

```json
{
  "provider": "DEEPSEEK",
  "model": "deepseek-chat"
}
```

当前 Python 服务通过 OpenAI 兼容接口调用这些提供商：

- `KIMI`
- `DEEPSEEK`
- `CHATGPT`
- `GEMINI`
- `GLM`
