<template>
  <main class="app-shell">
    <section class="hero-panel" aria-labelledby="page-title">
      <div class="hero-copy">
        <p class="eyebrow">LOCAL FORMAT CHECK</p>
        <h1 id="page-title">论文格式检查工作台</h1>
        <p class="hero-text">
          选择模型，上传格式规范文件和待检测论文，系统会先抽取规范规则，再生成错误统计分析和 Word 批注版论文。
        </p>
      </div>
      <div class="status-strip" aria-label="当前任务状态">
        <span class="status-dot" :class="statusClass"></span>
        <div>
          <strong>{{ statusTitle }}</strong>
          <span>{{ statusMessage }}</span>
        </div>
      </div>
    </section>

    <section class="workspace-grid" aria-label="格式检查操作区">
      <form class="operation-panel" @submit.prevent="submitTask">
        <div class="section-heading">
          <p>输入</p>
          <h2>检测任务</h2>
        </div>

        <div class="field-grid">
          <label class="field">
            <span>模型厂商</span>
            <select v-model="provider" :disabled="isBusy" @change="syncModelWithProvider">
              <option v-for="item in providerOptions" :key="item.provider" :value="item.provider">
                {{ providerLabels[item.provider] || item.provider }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>模型型号</span>
            <select v-model="model" :disabled="isBusy">
              <option v-for="item in modelOptionsForProvider" :key="modelName(item)" :value="modelName(item)">
                {{ modelName(item) }}
              </option>
            </select>
          </label>
        </div>

        <div class="workflow-selector" aria-label="检测流程">
          <button
            v-for="item in workflowOptions"
            :key="item.value"
            type="button"
            class="workflow-option"
            :class="{ active: workflow === item.value }"
            :disabled="isBusy"
            @click="workflow = item.value"
          >
            <strong>{{ item.label }}</strong>
            <span>{{ item.description }}</span>
          </button>
        </div>

        <div class="upload-stack">
          <label class="upload-box" :class="{ selected: standardFile }">
            <input
              type="file"
              accept=".pdf,.doc,.docx,.md"
              :disabled="isBusy"
              @change="onFileChange($event, 'standard')"
            />
            <Icon name="upload" />
            <span>上传格式规范文件</span>
            <strong>{{ standardFile ? standardFile.name : '.pdf / .doc / .docx / .md' }}</strong>
            <small v-if="standardFile">{{ formatFileSize(standardFile.size) }}</small>
          </label>

          <label class="upload-box" :class="{ selected: checkedFile }">
            <input
              type="file"
              accept=".docx"
              :disabled="isBusy"
              @change="onFileChange($event, 'checked')"
            />
            <Icon name="fileText" />
            <span>上传待检测论文</span>
            <strong>{{ checkedFile ? checkedFile.name : '当前版本仅支持 .docx' }}</strong>
            <small v-if="checkedFile">{{ formatFileSize(checkedFile.size) }}</small>
          </label>
        </div>

        <p v-if="validationMessage" class="form-message" role="alert">
          {{ validationMessage }}
        </p>

        <div class="action-row">
          <button class="primary-button" type="submit" :disabled="isBusy">
            <Icon :name="isBusy ? 'loader' : 'play'" />
            <span>{{ isBusy ? '检测中' : '开始检测' }}</span>
          </button>
          <button class="secondary-button" type="button" :disabled="isBusy" @click="resetTask">
            <Icon name="rotate" />
            <span>重置</span>
          </button>
        </div>
      </form>

      <section class="result-panel" aria-label="检测结果">
        <div class="section-heading compact">
          <p>输出</p>
          <h2>统计与下载</h2>
        </div>

        <div class="metrics-grid">
          <article class="metric">
            <span>错误总数</span>
            <strong>{{ statistics.total_issues ?? 0 }}</strong>
          </article>
          <article class="metric">
            <span>已检查规则</span>
            <strong>{{ statistics.checked_rule_count ?? 0 }}</strong>
          </article>
          <article class="metric">
            <span>需人工确认</span>
            <strong>{{ statistics.need_manual_confirmation ?? 0 }}</strong>
          </article>
        </div>

        <div class="download-row" aria-label="结果文件下载">
          <a
            v-for="file in downloadFiles"
            :key="file.fileType"
            class="download-link"
            :class="{ disabled: !file.url }"
            :href="file.url || undefined"
            :aria-disabled="!file.url"
          >
            <Icon name="download" />
            <span>{{ file.label }}</span>
          </a>
        </div>

        <div v-if="compareResult" class="compare-panel" aria-label="两种检测流程差异">
          <h3>两种流程差异</h3>
          <div class="compare-grid">
            <span>LLM 主导</span>
            <strong>{{ compareResult.llm_direct_total_issues ?? 0 }}</strong>
            <span>混合检测</span>
            <strong>{{ compareResult.hybrid_total_issues ?? 0 }}</strong>
            <span>程序新增</span>
            <strong>{{ compareResult.deterministic_added_issues ?? 0 }}</strong>
            <span>建议流程</span>
            <strong>{{ workflowLabels[compareResult.recommended_workflow] || compareResult.recommended_workflow || '-' }}</strong>
          </div>
          <p v-if="compareResult.reason">{{ compareResult.reason }}</p>
        </div>

        <div class="summary-columns">
          <div>
            <h3>按类别</h3>
            <ul class="distribution-list">
              <li v-for="item in categoryRows" :key="item.name">
                <span>{{ item.name }}</span>
                <strong>{{ item.value }}</strong>
              </li>
              <li v-if="categoryRows.length === 0" class="empty-line">暂无分类错误</li>
            </ul>
          </div>
          <div>
            <h3>按严重程度</h3>
            <ul class="distribution-list">
              <li v-for="item in severityRows" :key="item.name">
                <span>{{ severityLabels[item.name] || item.name }}</span>
                <strong>{{ item.value }}</strong>
              </li>
              <li v-if="severityRows.length === 0" class="empty-line">暂无严重程度统计</li>
            </ul>
          </div>
        </div>
      </section>
    </section>

    <section class="table-panel" aria-labelledby="table-title">
      <div class="table-head">
        <div>
          <p class="eyebrow">DETAILS</p>
          <h2 id="table-title">错误明细表</h2>
        </div>
        <span>{{ issues.length }} 条记录</span>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>位置</th>
              <th>类别</th>
              <th>错误原因</th>
              <th>规范要求</th>
              <th>修改建议</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(issue, index) in issues" :key="issue.issue_id || issue.id || index">
              <td>{{ issue.issue_id || issue.id || index + 1 }}</td>
              <td>{{ formatIssueLocation(issue) }}</td>
              <td>{{ issue.category || '-' }}</td>
              <td>{{ formatIssueReason(issue) }}</td>
              <td>{{ formatIssueRequirement(issue) }}</td>
              <td>{{ formatIssueSuggestion(issue) }}</td>
            </tr>
            <tr v-if="issues.length === 0">
              <td colspan="6" class="empty-table">检测完成后会在这里展示错误明细。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'

const fallbackModels = [
  { provider: 'deepseek', defaultModel: 'deepseek-chat' },
  { provider: 'chatgpt', defaultModel: 'gpt-4.1' },
  { provider: 'claudecode', defaultModel: 'claude-3-5-sonnet' },
  { provider: 'kimi', defaultModel: 'moonshot-v1-8k' },
  { provider: 'glm', defaultModel: 'glm-4' }
]

const providerLabels = {
  deepseek: 'DeepSeek',
  chatgpt: 'ChatGPT',
  claudecode: 'Claude Code',
  kimi: 'Kimi',
  glm: 'GLM'
}

const severityLabels = {
  high: '严重',
  medium: '一般',
  low: '轻微'
}

const models = ref(fallbackModels)
const provider = ref('deepseek')
const model = ref('deepseek-chat')
const workflow = ref('base_format')
const standardFile = ref(null)
const checkedFile = ref(null)
const status = ref('idle')
const statusMessage = ref('等待上传规范文件和待检测论文。')
const validationMessage = ref('')
const result = ref(null)

const isBusy = computed(() => status.value === 'running')

const workflowOptions = [
  {
    value: 'full_check',
    label: '完整检查',
    description: 'Python 检查硬格式，LLM 检查语义、语法、错别字和对应关系'
  },
  {
    value: 'hard_format',
    label: '硬格式检查',
    description: '字体、字号、页边距、页码、公式、图表等由 Python 检查'
  },
  {
    value: 'semantic_llm',
    label: '语义语言检查',
    description: '语法、语义、错别字、摘要对应、引用对应关系由 LLM 检查'
  }
]

workflowOptions.splice(
  0,
  workflowOptions.length,
  {
    value: 'base_format',
    label: '基础格式检查',
    description: 'Python 检查硬性格式，LLM 只解释复杂格式规则和渲染类证据'
  },
  {
    value: 'language_semantic',
    label: '语言语义拓展',
    description: 'LLM 检查语法、错别字、病句、语义一致性、引用与参考文献关系'
  },
  {
    value: 'full_check',
    label: '完整检查',
    description: '同时运行基础格式检查和语言语义拓展检查'
  }
)

const providerOptions = computed(() => {
  const seen = new Set()
  return models.value.filter((item) => {
    if (seen.has(item.provider)) return false
    seen.add(item.provider)
    return true
  })
})

const modelOptionsForProvider = computed(() => {
  return models.value.filter((item) => item.provider === provider.value && modelName(item))
})

const statistics = computed(() => result.value?.statistics || {})
const issues = computed(() => result.value?.issues || [])

const downloadFiles = computed(() => [
  {
    label: '格式规范 JSON',
    fileType: 'format_rule',
    url: result.value?.formatRule?.url
  },
  {
    label: '错误统计分析',
    fileType: 'analysis_doc',
    url: result.value?.analysisDoc?.url
  },
  {
    label: '批注版论文',
    fileType: 'annotated_paper',
    url: result.value?.annotatedDoc?.url
  }
])

const categoryRows = computed(() => objectToRows(statistics.value.by_category))
const severityRows = computed(() => objectToRows(statistics.value.by_severity))
const compareResult = computed(() => result.value?.compareResult || null)

const workflowLabels = {
  full_check: '完整检查',
  hard_format: '硬格式检查',
  semantic_llm: '语义语言检查',
  llm_direct: '语义语言检查',
  hybrid: '完整检查',
  compare: '完整检查'
}

Object.assign(workflowLabels, {
  base_format: '基础格式检查',
  language_semantic: '语言语义拓展',
  full_check: '完整检查',
  hard_format: '基础格式检查',
  semantic_llm: '语言语义拓展',
  llm_direct: '语言语义拓展',
  hybrid: '完整检查',
  compare: '完整检查'
})

const statusTitle = computed(() => {
  if (status.value === 'running') return '正在检测'
  if (status.value === 'completed') return '已完成'
  if (status.value === 'failed') return '检测失败'
  return '待开始'
})

const statusClass = computed(() => ({
  running: status.value === 'running',
  completed: status.value === 'completed',
  failed: status.value === 'failed'
}))

onMounted(async () => {
  try {
    const response = await fetch('/api/models')
    const payload = await response.json()
    if (payload?.success && Array.isArray(payload.data) && payload.data.length > 0) {
      models.value = payload.data
      provider.value = payload.data[0].provider
      model.value = modelName(payload.data[0])
    }
  } catch {
    statusMessage.value = '未连接后端时会使用本地默认模型列表。'
  }
})

function syncModelWithProvider() {
  const first = modelOptionsForProvider.value[0]
  model.value = first ? modelName(first) : ''
}

function modelName(item) {
  return item?.model || item?.defaultModel || ''
}

function formatIssueLocation(issue) {
  if (issue?.location_display) return cleanDisplayText(issue.location_display)
  const location = issue?.location
  if (typeof location === 'string') return cleanDisplayText(location || '-')
  if (!location || typeof location !== 'object') return cleanDisplayText(issue?.position || issue?.anchor_text || '-')
  const parts = []
  if (location.page) parts.push(`页码: ${location.page}`)
  if (location.section) parts.push(`章节: ${location.section}`)
  if (location.paragraph_index) parts.push(`段落: ${location.paragraph_index}`)
  if (location.object_type) parts.push(`对象: ${location.object_type}`)
  const quote = location.quote || issue?.anchor_text
  if (quote) parts.push(`锚点: ${quote}`)
  return cleanDisplayText(parts.length ? parts.join('；') : '-')
}

function formatIssueReason(issue) {
  return cleanDisplayText(issue?.reason || issue?.error_reason || issue?.problem || issue?.message || '-')
}

function formatIssueRequirement(issue) {
  return cleanDisplayText(issue?.expected || issue?.rule || issue?.requirement || issue?.expected_rule || '-')
}

function formatIssueSuggestion(issue) {
  return cleanDisplayText(issue?.suggestion || issue?.fix_suggestion || '-')
}

function cleanDisplayText(value) {
  if (value === null || value === undefined) return '-'
  const text = String(value)
  return text
    .replaceAll('娈佃惤', '段落')
    .replaceAll('瀵硅薄', '对象')
    .replaceAll('閿氱偣', '锚点')
    .replaceAll('绋嬪簭璇诲彇鍒扮殑瀹為檯鏍煎紡涓?', '程序读取到的实际格式为 ')
    .replaceAll('瑙勮寖瑕佹眰涓?', '规范要求为 ')
    .replaceAll(/璇峰皢璇ヤ綅缃.?皟鏁翠负/g, '请将该位置调整为')
}

function onFileChange(event, type) {
  const file = event.target.files?.[0] || null
  validationMessage.value = ''
  if (type === 'standard') {
    standardFile.value = file
    return
  }
  checkedFile.value = file
}

async function submitTask() {
  validationMessage.value = validateForm()
  if (validationMessage.value) return

  status.value = 'running'
  statusMessage.value = '正在抽取格式规则并检查论文，请等待 Python Agent 返回结果。'
  result.value = null

  const formData = new FormData()
  formData.append('provider', provider.value)
  formData.append('model', model.value)
  formData.append('workflow', workflow.value)
  formData.append('standardFile', standardFile.value)
  formData.append('checkedFile', checkedFile.value)

  try {
    const response = await fetch('/api/tasks/check', {
      method: 'POST',
      body: formData
    })
    const payload = await response.json()
    if (!response.ok || !payload.success) {
      throw new Error(payload?.message || '检测任务执行失败')
    }
    result.value = payload.data
    status.value = 'completed'
    statusMessage.value = `结果已保存到 ${payload.data.resultFolder}，可下载规则、分析文档和批注版论文。`
  } catch (error) {
    status.value = 'failed'
    statusMessage.value = error instanceof Error ? error.message : '检测任务执行失败'
  }
}

function validateForm() {
  if (!provider.value) return '请选择模型厂商。'
  if (!model.value) return '请选择模型型号。'
  if (!standardFile.value) return '请上传格式规范文件。'
  if (!checkedFile.value) return '请上传待检测论文。'
  if (!hasAllowedExtension(standardFile.value.name, ['pdf', 'doc', 'docx', 'md'])) {
    return '格式规范文件仅支持 .pdf、.doc、.docx、.md。'
  }
  if (!hasAllowedExtension(checkedFile.value.name, ['docx'])) {
    return '当前版本仅支持 .docx 待检测论文。'
  }
  return ''
}

function resetTask() {
  standardFile.value = null
  checkedFile.value = null
  result.value = null
  validationMessage.value = ''
  workflow.value = 'base_format'
  status.value = 'idle'
  statusMessage.value = '等待上传规范文件和待检测论文。'
}

function hasAllowedExtension(name, extensions) {
  const suffix = name.split('.').pop()?.toLowerCase()
  return Boolean(suffix && extensions.includes(suffix))
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function objectToRows(value) {
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).map(([name, itemValue]) => ({ name, value: itemValue }))
}

const iconPaths = {
  upload: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'M17 8l-5-5-5 5', 'M12 3v12'],
  fileText: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z', 'M14 2v6h6', 'M16 13H8', 'M16 17H8', 'M10 9H8'],
  play: ['M5 3l14 9-14 9V3z'],
  rotate: ['M21 2v6h-6', 'M3 12a9 9 0 0 1 15-6.7L21 8', 'M3 22v-6h6', 'M21 12a9 9 0 0 1-15 6.7L3 16'],
  download: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'M7 10l5 5 5-5', 'M12 15V3'],
  loader: ['M21 12a9 9 0 1 1-6.2-8.6']
}

const Icon = defineComponent({
  props: {
    name: {
      type: String,
      required: true
    }
  },
  setup(props) {
    return () =>
      h(
        'svg',
        {
          class: ['icon', props.name === 'loader' ? 'spin' : ''],
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': '2',
          'stroke-linecap': 'round',
          'stroke-linejoin': 'round',
          'aria-hidden': 'true'
        },
        (iconPaths[props.name] || []).map((d) => h('path', { d }))
      )
  }
})
</script>
