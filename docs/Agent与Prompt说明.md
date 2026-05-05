# Agent 与 Prompt 说明

## 1. 总览

系统使用两个串行 Agent：

- Agent1：负责从格式规范文件中抽取规则。
- Agent2：负责根据规则检查待检测论文。

两个 Agent 使用用户在前端选择的同一模型配置，也可以在后续扩展为分别选择模型。

## 2. Agent1：格式规范抽取

### 输入

- 模型厂商和型号。
- 格式规范文件。
- `extract_rule_prompt`。
- `extract-format-rules` skill。

### 处理步骤

1. 解析格式规范文件。
2. 将提取出的文本、结构、表格、模板示例传入 `extract_rule_prompt`。
3. 调用 LLM。
4. 要求 LLM 返回格式规范 JSON。
5. 使用 `skills/extract-format-rules/scripts/save_rule_json.py` 校验并保存 JSON。

### 输出

`format_rule.json`

该文件必须包含：

- `file_analysis`
- `rules`

## 3. Agent2：论文格式检查

### 输入

- 模型厂商和型号。
- Agent1 生成的 `format_rule.json`。
- 待检测论文 `checked_file`。
- `format_check_prompt`。
- `check-paper-format` skill。

### 处理步骤

1. 读取 `format_rule.json`。
2. 解析待检测论文的文本和格式信息。
3. 将规则和论文证据传入 `format_check_prompt`。
4. 调用 LLM。
5. 要求 LLM 返回检查结果 JSON。
6. 使用 `skills/check-paper-format/scripts/build_format_check_outputs.py` 生成结果文件。

### 输出

- `format_check_result.json`
- `format_check_analysis.docx`
- `format_check_analysis.md`
- `checked_file_annotated.docx`

## 4. Prompt 约束

### `extract_rule_prompt`

要求：

- 只抽取格式、排版、结构、引用、图表等规范。
- 不抽取和论文内容质量相关的规则。
- 输出合法 JSON。
- 不编造规范。
- 不确定的规则标记 `need_manual_confirmation: true`。

### `format_check_prompt`

要求：

- 只检查 `format_rule.json` 中存在的规则。
- 每个错误必须说明位置、规则、实际情况、错误原因和修改建议。
- 输出合法 JSON。
- 能定位到论文文本的位置应提供 `anchor_text`。
- 无法确定的问题标记 `need_manual_confirmation: true`。

## 5. Agent 失败处理

| 失败点 | 处理 |
| --- | --- |
| 文件解析失败 | 返回明确错误，不调用 LLM |
| LLM 调用失败 | 提示重试 |
| LLM 输出非法 JSON | 保存原始输出，提示重新生成 |
| JSON 缺少必要字段 | 阻止进入下一阶段 |
| 批注生成失败 | 保留统计文档和 JSON，提示批注失败原因 |

## 6. 与 skill 的关系

| Agent | Skill | 作用 |
| --- | --- | --- |
| Agent1 | `extract-format-rules` | 约束规范抽取流程、JSON 结构和保存方式 |
| Agent2 | `check-paper-format` | 约束格式检查流程、错误结构和结果文档生成方式 |

## 7. 已确认 Agent 实现方式

### 7.1 Java 与 Python 分工

- Java Spring Boot 负责接口，不直接调用 LLM。
- Python 负责 LLM API 调用、Agent 执行、文档属性读取、结果文档生成。
- Java 将上传文件路径、模型选择和输出目录传给 Python。
- Python 返回结构化 JSON 和结果文件路径。
- Java 使用 `ProcessBuilder` 启动 Python 脚本。当前不采用 Java 调用 Python HTTP 服务的方式。

### 7.2 模型支持

Python 侧支持外部 API 厂商：

- `deepseek`
- `chatgpt`
- `claudecode`
- `kimi`
- `glm`

API Key 和 Base URL 在 Python 配置中维护。

模型配置由两层组成：

- 主配置：`agent-service/config/llm_providers.json`，保存厂商、模型型号、API 协议、默认 Base URL、API Key 环境变量名。
- 本地覆盖配置：`agent-service/config/llm_providers.local.json`，保存本机自定义 Base URL、API Key 环境变量名或实际 API Key；该文件不提交。

当前厂商对照：

| 厂商 | API 协议 | 默认 Base URL | API Key 环境变量 |
| --- | --- | --- | --- |
| `deepseek` | OpenAI compatible | `https://api.deepseek.com` | `DEEPSEEK_API_KEY` |
| `chatgpt` | OpenAI | `https://api.openai.com/v1` | `OPENAI_API_KEY` |
| `claudecode` | Anthropic | `https://api.anthropic.com/v1` | `ANTHROPIC_API_KEY` |
| `kimi` | OpenAI compatible | `https://api.moonshot.ai/v1` | `MOONSHOT_API_KEY` |
| `glm` | OpenAI compatible | `https://api.z.ai/api/paas/v4` | `ZAI_API_KEY` |

本地设置命令：

```powershell
Set-Location E:\code\formatCheck\agent-service
python -m app.llm_config list
python -m app.llm_config show chatgpt
python -m app.llm_config set-url chatgpt https://api.openai.com/v1
python -m app.llm_config set-api-key chatgpt sk-xxxx
python -m app.llm_config set-api-key-env chatgpt OPENAI_API_KEY
```

Java 后端通过 `format-check.model-config` 读取同一份主配置，提供 `/api/models` 给前端，并在任务提交时校验模型厂商和型号。Python Agent 执行时读取主配置加本地覆盖配置，最终得到实际调用 LLM 所需的 `base_url`、`api_key_env` 和 `api_key`。

### 7.3 检查职责

- 格式规范抽取由 Agent1 + LLM 完成。
- Word 格式检查优先由程序完成，包括页边距、字体、字号、段落、标题、表格、页眉页脚、页码等可读取属性。
- 错别字和语法错误由 LLM 判断。
- 所有格式规则都应尽量转化为程序检查项；无法程序化的规则才交给 LLM 辅助判断并标记置信度。

### 7.4 批注要求

批注版论文必须使用 Word 原生批注。批注内容固定为：

```text
错误原因：{reason}
规范要求：{expected}
修改建议：{suggestion}
```

不得用正文追加、括号标记、颜色高亮等方式替代原生批注。

## 8. 成熟文档处理方法

当前两个业务 skill 已补充文档读取与操作方法：

- `skills/extract-format-rules/references/document_io_methods.md`
  - 用于规范文件读取。
  - `.docx` 使用 `python-docx` 读取正文、段落、表格，再用 WordprocessingML 读取样式、页边距、页眉页脚、编号等属性。
  - `.doc` 优先用 LibreOffice headless 转为 `.docx` 后再读取。
  - `.pdf` 使用 `pdfplumber`/`pypdf` 提取文本、表格、元数据，并用 Poppler 渲染页面做版式验证。
  - `.md`/`.txt` 直接读取并保留标题层级、列表、表格和行号。

- `skills/check-paper-format/references/document_io_methods.md`
  - 用于待检测论文读取、格式证据构建和批注写入。
  - 待检测论文 `.docx` 需要同时读取可见文本和 Word XML 属性。
  - 可程序化判断的格式项优先用程序判断，LLM 主要处理错别字、语法和模糊语义判断。
  - 批注版论文必须复制原论文后写入 Word 原生批注，不能覆盖原文件。
  - 批注内容固定为 `错误原因：...`、`规范要求：...`、`修改建议：...` 三段。
