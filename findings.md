# 发现记录

## 当前项目上下文

- 项目根目录：`E:\code\formatCheck`
- 当前已有 `docs/systemInfo.txt`。
- 当前已有两个项目 skill：
  - `skills/extract-format-rules`：从规范文件抽取结构化格式规则 JSON。
  - `skills/check-paper-format`：根据格式规则检查论文，并生成统计分析文档与批注版论文。
- `sourceFIle/` 中已有规范文件和论文样例。
- `results/` 中已有部分历史输出样例。

## 用户确认的系统理解

- 系统为单机版。
- 不考虑数据库、网络上线、多用户体系。
- 用户先选择模型厂商和型号。
- 用户上传文档格式规范文件和待检测论文 `checked_file`。
- Agent1 使用 `extract_rule_prompt` 和 `extract-format-rules` skill 生成 `format_rule`。
- `format_rule` 保存到本地项目结果目录，并在前端提供下载链接。
- Agent2 使用 `format_rule` 和 `checked_file`，调用 `check-paper-format` skill。
- Agent2 生成两个结果文件：
  - 错误统计分析文档。
  - 原论文副本上的批注版文档。
- 前端提供两个结果文件下载链接，并展示错误统计分析数据。

## 约束

- 尽量以 `.docx` 作为待检测论文格式，便于写入 Word 批注。
- 如果待检测文件不是 `.docx`，当前版本直接拒绝，不进入检测流程。
- LLM 输出必须经过 JSON 解析和结构校验后再生成文件。
- 原始上传文件不可被覆盖。

## 文档组织结论

- 开发文档按单机版闭环组织，不包含数据库和上线部署设计。
- 文档分为需求、架构、流程、数据结构、前端交互、Agent 与 Prompt、文件目录、测试用例和索引。
- 后续开发可以先从 `docs/开发文档索引.md` 进入。

## 2026-04-28 补充确认

- 前端技术栈：Vue。
- 后端接口层：Java Spring Boot。
- LLM 交互层：Python。
- Java 负责接口，Python 负责调用 LLM 外部 API。
- 支持模型厂商：`deepseek`、`chatgpt`、`claudecode`、`kimi`、`glm`。
- API Key 和 Base URL 在 Python 中配置，前端不支持配置。
- 规范文件必须支持 `.pdf`、`.doc`、`.docx`、`.md`。
- 待检测论文当前只支持 `.docx`。
- 格式检查优先由程序读取 Word 样式、页边距、字体、段落等属性完成。
- 所有可程序化格式规则都应由程序判断。
- 错别字和语法错误由 LLM 判断。
- 分析文档输出 `.docx`。
- 批注版论文必须使用 Word 原生批注。
- 批注内容固定为：错误原因 + 规范要求 + 修改建议。
- 前端展示错误明细表。
- 前端不预览原始文件，但保存原始文件。
- 不允许用户编辑 `format_rule.json` 后继续检查。
- 结果目录按待检测文档名命名，保留，不自动清理。
- Java 启动 Python 的方式确定为 `ProcessBuilder` 启动脚本，不是 Python HTTP 服务。
- 文件存储目录按待检测文件分组，例如 `善意想/待检测文件`、`善意想/规则规范文件`、`善意想/规则抽取结果`、`善意想/检测结果`、`善意想/检测后带批注的源文件`。
- `杨丽` 不是需求，不写入功能范围。
