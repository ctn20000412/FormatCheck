# Python 与 LLM 检查规则清单

本文档说明当前项目中两类检查规则的职责边界、规则分流方式、已实现能力和后续扩展点。对应核心代码在 `agent-service/app/main.py`，前端和 Java 后端通过 `workflow` 选择检查流程。

## 一、规则分流原则

系统从规范文件中抽取 `format_rule.json` 后，会把每条规则归类为 `python` 或 `llm`。

规则字段建议显式写入：

```json
{
  "rule_id": "R001",
  "category": "font",
  "description": "中文正文使用宋体，英文正文使用 Times New Roman",
  "expected": "中文：宋体；英文：Times New Roman",
  "check_method": "python"
}
```

如果规则没有 `check_method`，系统会根据关键词自动推断：

- 命中以下关键词时归为 `python`：`font`、`size`、`margin`、`spacing`、`indent`、`alignment`、`page`、`table`、`heading`、`formula`、`字体`、`字号`、`页边距`、`行距`、`缩进`、`对齐`、`页码`、`表格`、`标题`、`公式`。
- 未命中上述硬格式关键词时归为 `llm`。

兼容写法：

- `python`、`deterministic`、`hard_format`、`rule_engine` 会归一为 `python`。
- `llm`、`llm_semantic`、`semantic`、`semantic_llm` 会归一为 `llm`。

## 二、Python 检查规则

Python 负责硬性的、可通过 Word 文件结构或渲染结果确定的格式规则。核心入口是 `run_hard_format_from_rule()`。

### 1. 已实现自动判错的规则

#### 1.1 内容归属与 scope 分域检查

检查对象：

- Word 段落。
- `format_rule.json` 中每条规则的 `scope`。

当前会把段落识别为以下逻辑归属：

- `body`：正文。
- `heading`：标题通用。
- `heading_1`：一级标题。
- `heading_2`：二级标题。
- `heading_3`：三级标题。
- `abstract`：摘要、关键词。
- `toc`：目录。
- `reference`：参考文献。
- `table_caption`：表格标题。
- `figure_caption`：图片标题。
- `formula`：公式段落。
- `blank`：空段落。

规则 scope 会归一为对应归属：

- `正文` -> `body`
- `一级标题（章标题）` -> `heading_1`
- `二级标题（节标题）` -> `heading_2`
- `三级标题（条标题）` -> `heading_3`
- `摘要`、`中文摘要正文`、`英文摘要` -> `abstract`
- `目录` -> `toc`
- `参考文献列表` -> `reference`
- `表格标题`、`表题` -> `table_caption`
- `图题`、`图片标题` -> `figure_caption`
- `公式` -> `formula`

重要逻辑：

- 一级标题规则只检查一级标题段落。
- 二级标题规则只检查二级标题段落。
- 正文规则只检查正文段落。
- 表格、图片、页眉、页脚等对象规则不会再错误套用到所有正文段落。

#### 1.2 字体检查

检查对象：

- Word 段落中的 run。
- 直接格式和样式继承后的有效字体属性。

当前支持识别的目标字体：

- 中文字体：`宋体`、`SimSun`、`黑体`、`楷体`。
- 英文字体：`Times New Roman`、`Arial`、`Calibri`。

重要逻辑：

- 如果规则要求英文为 `Times New Roman`，系统只检查包含拉丁字母的 run。
- 如果规则要求中文为 `宋体`、`黑体`、`楷体` 等，系统只检查包含中文字符的 run。
- 这样可以避免把中文文本误判为必须使用 `Times New Roman`。

输出问题字段：

- `source`: `deterministic_checker`
- `location.paragraph_index`: Word 段落序号
- `anchor_text`: 用于批注定位的原文片段
- `reason`: 程序读取到的实际字体
- `expected`: 规范要求字体
- `suggestion`: 修改建议

#### 1.3 字号检查

检查对象：

- Word 段落 run 的 `effective_properties.font_size_pt`。

当前支持识别的字号：

- 中文字号：`初号`、`小初`、`一号`、`小一`、`二号`、`小二`、`三号`、`小三`、`四号`、`小四`、`五号`、`小五`、`六号`、`小六`、`七号`、`八号`。
- 数值字号：`12 pt`、`12 磅` 等。

重要逻辑：

- 字号检查同样受 scope 限制。
- 例如 `正文小四号` 只检查正文段落，不检查一级标题。

#### 1.4 段落对齐检查

检查对象：

- Word 段落的 `effective_paragraph_properties.alignment`。

当前支持识别：

- `居中`、`center`
- `左对齐`、`居左`、`left`
- `右对齐`、`居右`、`right`
- `两端对齐`、`justify`

重要逻辑：

- 对齐检查同样受 scope 限制。
- 例如 `一级标题居中` 只检查一级标题，不检查正文。

#### 1.5 段落行距、段前段后、缩进检查

检查对象：

- Word 段落的 `effective_paragraph_properties.spacing`。
- Word 段落的 `effective_paragraph_properties.indent`。

当前支持识别：

- 单倍行距。
- 1.5 倍行距。
- 段前、段后为 0。
- 段前、段后指定磅值或“行”值。
- 首行缩进指定字符数或磅值。

重要逻辑：

- 这些检查同样受 scope 限制。
- 例如 `正文为1.5倍行距` 只检查正文段落。
- `标题段前段后各0.5行` 只检查标题段落。

#### 1.6 页面设置检查

检查对象：

- Word section 页面设置。

当前支持：

- A4 纸张尺寸。
- 上、下、左、右页边距。
- 页眉距边界。
- 页脚距边界。

说明：

- `.docx` 静态结构可以读取 section 的纸张尺寸和边距。
- 封面无页码、奇偶页实际显示页眉等需要结合渲染结果，仍列为需要进一步增强的项目。

#### 1.7 页眉页脚和页码检查

检查对象：

- Word 页眉、页脚关系部件。
- 页脚中的 PAGE 域。

当前支持：

- 检查页眉是否存在。
- 检查页眉内容是否为空。
- 检查页脚是否存在。
- 检查页脚是否包含 PAGE 页码域。
- 对页眉字体字号规则输出人工确认项，因为当前页眉解析主要读取文本和域，未稳定读取页眉 run 格式。

#### 1.8 表格检查

检查对象：

- 表格结构。
- 表格边框。
- 表格标题段落。

当前支持：

- 表题是否可识别。
- 表题编号格式，例如 `表2-1`。
- 表题是否居中。
- 三线表基础判断：是否有顶线、底线，是否存在竖线。
- 表格边框证据输出。

说明：

- 跨页表格是否重复表头需要渲染页结果，当前不能仅靠静态 `.docx` 稳定判断。

#### 1.9 图片检查

检查对象：

- Word 图片关系。
- 图题段落。

当前支持：

- 图片数量。
- 图题是否可识别。
- 图题编号格式，例如 `图2-1`。
- 图题是否居中。
- 图片数量多于图题数量时输出问题。
- 图片清晰度/DPI 规则会输出人工确认项。

说明：

- 部分 `.docx` 图片不包含可靠 DPI 或实际排版尺寸，清晰度需要读取图像元数据、渲染结果或人工确认。

#### 1.10 参考文献和正文引用检查

检查对象：

- 正文引用编号。
- 参考文献列表编号。

当前支持：

- 正文引用格式识别，例如 `[1]`、`[1,2]`、`[1-3]`。
- 参考文献列表项编号识别，例如 `[1] 作者. 题名.`。
- 正文引用编号是否能在参考文献列表中找到。
- 参考文献列表项是否缺少 `[编号]`。

说明：

- 期刊、会议、书籍等参考文献类型的完整语义分类和复杂格式仍建议交给 LLM 或后续专门模板。

#### 1.11 公式编号和公式格式检查

检查对象：

- `.docx` 内部的 Office Open XML 公式节点：`m:oMath`、`m:oMathPara`。
- 被识别为 `formula` 的段落。

触发条件：

- 规则归类为 `python`。
- 规则内容包含 `formula` 或 `公式`。
- 规则内容包含 `number` 或 `编号`。

当前判错能力：

- 发现公式段落。
- 检查公式段落文本中是否存在形如 `(1)`、`（1）`、`(1.1)`、`（1-1）` 的编号。
- 如果公式没有编号，则输出错误。
- 检查公式编号是否为章号-序号格式，例如 `(3-1)`。
- 检查公式段落是否居中。

输出问题字段：

- `source`: `python_hard_checker`
- `location.object_type`: `formula`
- `location.paragraph_index`: 公式所在段落序号
- `anchor_text`: 公式段落文本或“公式”

#### 1.12 目录检查

检查对象：

- 论文目录中的标题和页码。
- Word 标题段落。
- 通过 LibreOffice 渲染出的 PDF 页码结果。

触发条件：

- 规则归类为 `python`。
- 规则内容包含 `toc`、`目录` 或 `页码`。

当前判错能力：

- 从全文中识别目录行，例如标题后带点线或空白并以页码结尾。
- 从 Word 段落样式或标题编号中识别正文标题。
- 调用 `soffice --headless --convert-to pdf` 将 DOCX 渲染成 PDF。
- 使用 `PyMuPDF` 的 `fitz` 读取 PDF 页面文本。
- 比较目录声明页码和渲染后的正文实际页码。
- 检查目录标题、点线和页码格式的基础证据。

依赖说明：

- 需要本机可执行 `soffice`。
- 需要 Python 环境安装 `PyMuPDF`。
- 如果依赖缺失，当前逻辑会跳过该项检查，不会报错中断。

#### 1.13 覆盖率审计

Python 检查结果中会输出 `coverage_audit`，用于说明系统是否存在未覆盖的硬格式规则。

审计内容包括：

- 已实现 Python 检查器。
- 当前文档证据数量，例如段落数、节数、表格数、图片数、页眉页脚数、脚注尾注数。
- 段落归属统计，例如正文、标题、目录、摘要等数量。
- Python 规则总数。
- LLM 规则总数。
- 每类规则需要的检查器数量。
- 暂无专门判错器的 Python 规则。
- 需要继续补充检查器的领域。

当前已实现 Python 检查器：

- `font_family`
- `font_size`
- `paragraph_alignment`
- `paragraph_spacing`
- `paragraph_indent`
- `page_setup`
- `header`
- `footer_page_number`
- `table_format`
- `figure_format`
- `citation_reference`
- `formula_missing_number`
- `toc_rendered_page_consistency`

当前已明确标记为需要渲染或人工确认的项目：

- `odd_even_rendered_header_validation`：奇偶页实际页眉显示检查。
- `cover_page_rendered_no_page_number_validation`：封面/声明页实际无页码检查。
- `cross_page_table_repeat_header_validation`：跨页表格重复表头检查。
- `reliable_image_dpi_validation`：可靠图片 DPI 检查。
- `full_reference_style_semantic_classification`：参考文献类型和完整著录格式语义分类。

### 2. 已提取证据但判错器仍可继续完善的规则

当前 `build_docx_analysis_evidence()` 已经从 `.docx` 中提取大量结构化证据，这些证据可以继续扩展成更多 Python 判错器。

已提取的证据包括：

- 文档包结构：`package_parts_present`
- 段落总数：`paragraph_count`
- 节总数：`section_count`
- 表格总数：`table_count`
- 样式定义：`styles`
- 编号定义：`numbering`
- 页面与节设置：`sections`
- 段落与 run：`paragraphs`
- run 直接属性和样式继承属性：`direct_properties`、`effective_properties`
- 段落属性：缩进、对齐、行距、段前段后等
- 表格结构和边框：`tables`
- 页眉页脚：`headers_footers`
- 域代码：`fields`
- 脚注尾注：`footnotes`、`endnotes`
- 图片：`images`
- 超链接：`hyperlinks`
- 全文文本：`full_text`

建议继续增强的 Python 硬格式规则：

- Word 渲染后页面级检查：封面无页码、奇偶页页眉、跨页表格、图表跨页。
- 图片元数据检查：DPI、实际显示尺寸、图片清晰度。
- 参考文献模板化检查：期刊、会议、书籍、学位论文等不同类型的精确著录模板。
- 公式编号连续性和右对齐位置检查。

这些项目适合由 Python 完成，因为它们有明确、可量化的 Word 属性或渲染结果。

## 三、LLM 检查规则

LLM 负责不能稳定通过 Word 静态属性判断、需要语言理解或上下文推理的规则。核心入口是 `run_semantic_check_from_rule()`。

### 1. 当前 LLM 明确负责的规则

LLM 提示词中明确限定其只检查以下内容：

- 语法错误。
- 语义不通顺。
- 错别字。
- 中英文摘要对应关系。
- 正文引用与参考文献对应关系。
- 术语一致性。

示例规则：

```json
{
  "rule_id": "L001",
  "category": "language",
  "description": "论文语言应通顺，无明显错别字、病句和语义矛盾",
  "expected": "文字表达准确，术语前后一致",
  "check_method": "llm"
}
```

### 2. LLM 禁止检查的规则

当前系统提示词要求 LLM 不检查以下硬格式：

- 字体。
- 字号。
- 页边距。
- 页码。
- 行距。
- 表格边框。
- 公式编号。

这些规则必须交给 Python 程序检查，避免 LLM 根据文本猜测 Word 格式。

### 3. LLM 输入内容

LLM 当前收到的主要内容包括：

- LLM 语义类规则 JSON。
- 论文全文文本。
- 全文读取概况 JSON。
- python-docx 结构化证据摘要。
- 定位要求。

定位要求：

- 每个问题尽量填写 `location.paragraph_index`。
- `anchor_text` 必须是论文中的连续原文短语。
- `anchor_text` 用于后续在 Word 原文中插入原生批注。

### 4. LLM 输出要求

LLM 必须输出合法 JSON，主要字段包括：

- `paper_analysis`
- `statistics`
- `issues`

每个 `issue` 必须包含：

- `location`
- `anchor_text`
- `reason`
- `expected`
- `suggestion`
- `comment_text`

其中 `comment_text` 应用于 Word 批注，建议固定为：

```text
错误原因：...
规范要求：...
修改建议：...
```

## 四、检查流程

前端和 Java 后端支持三种主要流程。

### 1. `hard_format`

只执行 Python 硬格式检查。

适用场景：

- 只想验证字体、字号、页边距、页码、公式、表格等格式问题。
- 不希望调用 LLM。
- 需要较稳定、可复现的检查结果。

### 2. `semantic_llm`

只执行 LLM 语义语言检查。

适用场景：

- 只关注语法、语义、错别字、摘要对应、引用对应、术语一致性。
- 不检查 Word 硬格式。

### 3. `full_check`

先执行 Python 硬格式检查，再执行 LLM 语义语言检查，最后合并问题。

适用场景：

- 推荐的完整论文检查流程。
- 用 Python 保证硬格式准确性。
- 用 LLM 补充语言和语义层面的检查。

## 五、标准规则文件建议格式

为了提高检查准确率，建议 `format_rule.json` 中每条规则都包含以下字段：

```json
{
  "rule_id": "R001",
  "category": "font",
  "scope": "body_paragraph",
  "description": "正文中文使用宋体，英文和数字使用 Times New Roman",
  "expected": {
    "cjk_font": "宋体",
    "latin_font": "Times New Roman",
    "font_size_pt": 12
  },
  "check_method": "python",
  "severity": "medium"
}
```

LLM 规则示例：

```json
{
  "rule_id": "L001",
  "category": "abstract_consistency",
  "scope": "abstract",
  "description": "中文摘要和英文摘要的核心研究对象、方法、结论应一致",
  "expected": "中英文摘要表达的信息一致，不得遗漏核心结论",
  "check_method": "llm",
  "severity": "high"
}
```

关键建议：

- `check_method` 必须显式写入，减少自动推断误分流。
- Python 规则尽量结构化，避免只写自然语言。
- LLM 规则可以保留自然语言描述，但需要明确检查边界。

## 六、准确率优化建议

1. 把标准规范文件最终保存为结构化 JSON，而不是只保存 LLM 原始回答。
2. 每条规则明确 `scope`，例如 `cover`、`abstract`、`body`、`heading_1`、`table_caption`、`reference`。
3. 每条硬格式规则明确可比较字段，例如 `font`、`font_size_pt`、`line_spacing`、`alignment`、`margin_cm`。
4. Python 判错器按规则类型逐项实现，不让 LLM 判断硬格式。
5. LLM 只处理语法、语义、错别字、对应关系、术语一致性。
6. 对需要页码的检查，使用 Word/LibreOffice 渲染后的 PDF 页面结果，不能只依赖 `.docx` 静态 XML。
7. 对公式检查，继续基于 Office Open XML 的 `m:oMath`、`m:oMathPara` 扩展编号连续性和对齐方式检查。
## 最新拆分：基础功能点与拓展功能点

当前系统把原先的格式检查 LLM Agent 拆成两个 Agent：

- `agent2_complex_format_explainer`：属于基础功能点，和 Python 硬格式检查合并使用。Python 负责可确定的格式检查，LLM 只负责复杂格式规则解释、渲染类证据解释、WordprocessingML/LibreOffice/PDF/图片元数据等难以直接规则化的格式判断，不检查错别字、语法、病句或语义一致性。
- `agent3_language_semantic_checker`：属于拓展功能点，单独负责语法、错别字、病句、语义一致性、摘要与正文对应关系、术语一致性、引用与参考文献关系等语言语义问题，不再承担硬性格式检查。

前端、Java 后端和 Python Agent 统一使用以下工作流：

- `base_format`：基础格式检查，执行 Python 硬格式检查 + `agent2_complex_format_explainer`。
- `language_semantic`：语言语义拓展检查，只执行 `agent3_language_semantic_checker`。
- `full_check`：完整检查，依次执行 `base_format` 与 `language_semantic` 并合并结果。

兼容旧参数：

- `hard_format` 会自动归一为 `base_format`。
- `semantic_llm` 和 `llm_direct` 会自动归一为 `language_semantic`。
- `hybrid` 和 `compare` 会自动归一为 `full_check`。
