# 进度记录

## 2026-04-28

- 使用 `superpowers:brainstorming` 明确开发文档范围。
- 使用 `planning-with-files-zh` 创建项目规划文件。
- 已确认文档面向单机版开发，不包含数据库、上线部署、多用户系统。
- 开始编写开发文档。
- 已创建 8 份开发文档：需求、架构、流程、数据结构、前端交互、Agent 与 Prompt、本地文件目录、测试用例。
- 已执行完整性检查，未发现未完成标记残留。
- 已补充 `docs/开发文档索引.md`。
- 已更新 `task_plan.md` 和 `findings.md`，记录完成状态和文档组织结论。
- 根据用户补充的技术栈和业务约束，更新需求、架构、流程、数据结构、前端、Agent、目录和测试文档。
- 将结果目录规则从 session 命名调整为按待检测文档名命名。
- 根据用户纠正，进一步将结果目录内部结构调整为五类中文业务目录，并确认 Java 通过 `ProcessBuilder` 启动 Python。

## 2026-04-29

- 已按“Java 接口层 + Java 启动 Python 脚本”的方式完成后端首版骨架代码。
- 已实现模型厂商校验：`deepseek`、`chatgpt`、`claudecode`、`kimi`、`glm`。
- 已实现上传文件校验：规范文件支持 `.pdf`、`.doc`、`.docx`、`.md`，待检测论文当前仅支持 `.docx`。
- 已实现按待检测文件名分组的本地目录结构：`待检测文件`、`规则规范文件`、`规则抽取结果`、`检测结果`、`检测后带批注的源文件`。
- 已实现 Java 写入 `agent_request.json`，并通过 `ProcessBuilder` 调用 Python CLI。
- 已实现 Python 占位 Agent 入口，当前可生成 `format_rule.json`、错误统计分析 `.docx`、批注版论文副本和结构化统计数据。
- 已完成验证：Python 单测 3 个通过；Java `mvn clean test` 重新编译 16 个主源码文件、3 个测试文件，5 个单测通过。
- 已补充服务级主流程测试：验证 Java 保存上传文件、生成五类业务目录、启动 Python、返回下载链接；当前 Java 单测共 6 个通过，Python 单测 3 个通过。
- 说明：真实 LLM 调用、Word 样式解析、原生批注写入和完整规则引擎尚未实现，后续应在当前接口和目录结构上继续补齐。
- 已新增 Vue/Vite 前端单页工作台：模型选择、规范文件上传、待检测论文上传、任务提交、状态展示、错误统计、错误明细表和三个结果下载入口。
- 前端已按单机版约束实现：不暴露 API Key、Base URL、temperature、max_tokens，不提供 `format_rule.json` 编辑入口，不预览原始上传文件。
- 已完成前端验证：`npm.cmd run build` 构建通过；本地 Vite 服务已启动并通过 `http://127.0.0.1:5173` 返回 HTTP 200。
- 已新增 `docs/系统操作手册.md`，覆盖本地环境准备、前后端启动、页面操作、结果目录、验证命令、常见问题和演示流程。
- 已增强本地两个业务 skill 的文档处理方法：新增 DOCX/DOC/PDF/Markdown 读取与证据包构建规程，要求 DOCX 使用 `python-docx` + WordprocessingML，PDF 使用 `pdfplumber`/`pypdf` + Poppler 渲染验证，并强化批注版论文的 Word 原生批注格式校验。
- 已修复 Spring Boot 启动时 `format-check.storage-root` 和 `format-check.python-script` 绑定到 `java.nio.file.Path` 失败的问题：配置字段改为字符串，业务代码内部再转换为本地文件系统路径。
- 已修复前端模型型号下拉为空的问题：后端模型 DTO 字段统一为 `model`，前端同时兼容 `model` 和旧字段 `defaultModel`。
- 新增 Python 侧 LLM 厂商配置表 `agent-service/config/llm_providers.json`，覆盖 DeepSeek、ChatGPT、Claude、Kimi、GLM 的模型型号、API 协议、默认 Base URL 和 API Key 环境变量。
- 新增本地覆盖配置能力 `agent-service/config/llm_providers.local.json`，并加入忽略提交规则；支持通过 `python -m app.llm_config list/show/set-url/set-api-key/set-api-key-env` 设置本机 URL 和 Key。
- 新增 Java `ModelCatalogService`，后端 `/api/models` 和任务提交模型校验改为读取同一份模型配置，避免前端可选模型与 Python 实际配置不一致。
- 已完成验证：Python 单测 6 个通过；Java 后端 `mvn test` 8 个单测通过。
