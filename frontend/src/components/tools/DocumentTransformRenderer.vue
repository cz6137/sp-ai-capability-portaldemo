<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { CircleCheck, CopyDocument, Document, Download, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CapabilityManifestCard from '../CapabilityManifestCard.vue'
import ToolDocumentPreview from './ToolDocumentPreview.vue'
import ToolExperienceShell from './ToolExperienceShell.vue'
import ToolFileDropzone from './ToolFileDropzone.vue'
import ToolPanel from './ToolPanel.vue'
import type { ToolDefinition } from '../../capabilityManifest'
import { executeToolOperation } from '../../tools/runtime/executors'
import { downloadBlob, ensureExtension, fileStem, normalizeMarkdown } from '../../utils/documentConversion'

type PreviewHandle = { getElement: () => HTMLElement | undefined }
const props = defineProps<{ tool: ToolDefinition }>()
const tool = computed(() => props.tool)
const mode = ref(props.tool.runtime.operations[0]?.id || '')
const preview = ref<PreviewHandle>()
const selectedFile = ref<File>()
const sourceMarkdown = ref('')
const resultMarkdown = ref('')
const processing = ref(false)
const warnings = ref<string[]>([])
const failure = ref('')

const modes = computed(() => tool.value.runtime.operations)
const activeMode = computed(() => modes.value.find(item => item.id === mode.value) || modes.value[0])
const markdownSourceMode = computed(() => activeMode.value.input.kind === 'markdown')
const markdown = computed(() => markdownSourceMode.value ? sourceMarkdown.value : resultMarkdown.value)
const hasContent = computed(() => Boolean(markdown.value.trim()))
const outputName = computed(() => fileStem(selectedFile.value?.name || firstHeading(markdown.value) || '转换文档'))
const fileLimitMb = computed(() => tool.value.runtime.fileLimitMB)
const acceptAttribute = computed(() => activeMode.value.input.accept.map(item => `.${item}`).join(','))

watch(mode, () => {
  selectedFile.value = undefined
  resultMarkdown.value = ''
  warnings.value = []
  failure.value = ''
})
onMounted(() => loadSample(false))

async function acceptFile(file: File) {
  if (processing.value) { ElMessage.warning('当前转换尚未完成，请稍后选择文件'); return }
  clear()
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!activeMode.value.input.accept.includes(extension)) { failure.value = `当前模式仅支持 ${acceptAttribute.value}`; return }
  if (file.size > fileLimitMb.value * 1024 * 1024) { failure.value = `文件不能超过 ${fileLimitMb.value} MB`; return }
  if (file.size === 0) { failure.value = '文件为空，请选择包含内容的文件'; return }
  selectedFile.value = file
  warnings.value = []
  if (markdownSourceMode.value) {
    processing.value = true
    try { sourceMarkdown.value = await file.text(); ElMessage.success('Markdown 文件已载入，可预览后导出') }
    catch { selectedFile.value = undefined; sourceMarkdown.value = ''; failure.value = '文件读取失败，请重新选择'; ElMessage.error(failure.value) }
    finally { processing.value = false }
  } else await runOperation()
}

async function runOperation() {
  if (processing.value) return
  processing.value = true
  warnings.value = []
  failure.value = ''
  if (!markdownSourceMode.value) resultMarkdown.value = ''
  try {
    await nextTick()
    const result = await executeToolOperation({ operation: activeMode.value, sourceText: sourceMarkdown.value, sourceFile: selectedFile.value, previewElement: preview.value?.getElement(), outputName: outputName.value })
    if (result.markdown !== undefined) resultMarkdown.value = result.markdown
    warnings.value = result.warnings
    ElMessage.success(result.message)
  } catch (error) {
    failure.value = error instanceof Error && /[\u3400-\u9fff]/.test(error.message) ? error.message : `${activeMode.value.from} 转换失败，文件可能损坏、加密或不兼容，请核对后重试`
    ElMessage.error(failure.value)
  } finally { processing.value = false }
}

function downloadMarkdown() {
  if (!resultMarkdown.value.trim()) return
  downloadBlob(new Blob([normalizeMarkdown(resultMarkdown.value)], { type: 'text/markdown;charset=utf-8' }), ensureExtension(outputName.value, 'md'))
}
async function copyMarkdown() {
  if (!resultMarkdown.value.trim()) return
  try { await navigator.clipboard.writeText(resultMarkdown.value); ElMessage.success('Markdown 已复制') }
  catch { ElMessage.warning('浏览器未允许复制，请手动选择文本') }
}
function clear() {
  if (processing.value) return
  selectedFile.value = undefined
  warnings.value = []
  failure.value = ''
  if (markdownSourceMode.value) sourceMarkdown.value = ''
  else resultMarkdown.value = ''
}
function loadSample(message = true) {
  if (processing.value) return
  selectedFile.value = undefined
  failure.value = ''
  warnings.value = []
  sourceMarkdown.value = tool.value.runtime.sampleMarkdown || ''
  if (message) ElMessage.success('示例 Markdown 已载入')
}
function firstHeading(value: string) { return value.match(/^#\s+(.+)$/m)?.[1]?.trim() || '' }
</script>

<template>
  <ToolExperienceShell :tool="tool" eyebrow="LOCAL DOCUMENT CONVERSION" banner-tone="safe" :banner-text="tool.runtime.securityStatement">
    <nav class="mode-tabs surface" aria-label="转换类型">
      <button v-for="item in modes" :key="item.id" :class="{ active: mode === item.id }" type="button" :disabled="processing" @click="mode = item.id">
        <span>{{ item.from }}</span><b>→</b><span>{{ item.to }}</span><small>{{ item.hint }}</small>
      </button>
    </nav>

    <ToolPanel step="01" :title="activeMode.input.heading" :description="activeMode.input.description">
      <template #tools><el-button v-if="markdownSourceMode" :icon="Document" @click="loadSample()">载入示例</el-button><el-button v-if="hasContent || selectedFile" text :icon="RefreshRight" @click="clear">清空</el-button></template>
      <ToolFileDropzone :file="selectedFile" :accept="acceptAttribute" :max-mb="fileLimitMb" :compact="markdownSourceMode" :title="`选择或拖入 ${activeMode.from} 文件`" @select="acceptFile" />
      <div v-if="markdownSourceMode" class="md-editor"><textarea v-model="sourceMarkdown" spellcheck="false" aria-label="Markdown 编辑器" placeholder="# 在这里输入 Markdown"></textarea></div>
      <el-alert v-for="notice in activeMode.notices || []" :key="notice" :title="notice" type="warning" :closable="false" show-icon />
    </ToolPanel>

    <ToolPanel step="02" :title="markdownSourceMode ? '预览并导出' : '检查 Markdown 结果'" description="所有工具都在同一结果区完成预览、人工检查和导出；复杂版式请在下载前复核。">
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
      <div class="result-grid">
        <div v-if="!markdownSourceMode" class="result-source"><header><b>Markdown 源码</b><span>{{ resultMarkdown.length.toLocaleString() }} 个字符</span></header><textarea v-model="resultMarkdown" spellcheck="false" :disabled="processing" placeholder="转换结果将在这里显示"></textarea></div>
        <ToolDocumentPreview ref="preview" :class="{ full: markdownSourceMode }" :markdown="markdown" />
      </div>
      <div v-if="warnings.length" class="warning-list"><el-icon><WarningFilled /></el-icon><div><b>转换提示</b><p v-for="item in warnings" :key="item">{{ item }}</p></div></div>
      <template #footer>
        <span v-if="processing" class="processing"><el-icon class="is-loading"><RefreshRight /></el-icon>正在本地处理，请稍候</span>
        <el-button v-if="activeMode.output.kind === 'file'" type="primary" size="large" :icon="Download" :disabled="!hasContent" :loading="processing" @click="runOperation">{{ activeMode.output.actionLabel }}</el-button>
        <template v-else><el-button size="large" :icon="CopyDocument" :disabled="!hasContent" @click="copyMarkdown">复制 Markdown</el-button><el-button type="primary" size="large" :icon="Download" :disabled="!hasContent" @click="downloadMarkdown">{{ activeMode.output.actionLabel }}</el-button></template>
      </template>
    </ToolPanel>

    <template #aside>
      <section class="surface side-panel"><h3><el-icon><CircleCheck /></el-icon>首版支持范围</h3><dl><dt>处理位置</dt><dd>当前浏览器</dd><dt>文件上传</dt><dd>不上传</dd><dt>文件留存</dt><dd>不留存</dd><dt>文件上限</dt><dd>{{ fileLimitMb }} MB</dd></dl></section>
      <section class="surface side-panel boundary-panel"><h3><el-icon><WarningFilled /></el-icon>格式边界</h3><p v-for="note in tool.runtime.supportNotes" :key="note">{{ note }}</p></section>
      <CapabilityManifestCard :asset="tool" />
    </template>
  </ToolExperienceShell>
</template>

<style scoped>
.mode-tabs{padding:10px;display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:8px}.mode-tabs button{min-height:94px;padding:15px 12px;display:grid;grid-template-columns:1fr auto 1fr;align-content:center;align-items:center;gap:6px;border:1px solid transparent;border-radius:6px;background:#f6f8fb;color:#536176;cursor:pointer}.mode-tabs button:hover{border-color:#d3a4a4;background:#fffafa}.mode-tabs button.active{color:var(--brand-deep);border-color:#d58d8d;background:var(--brand-soft);box-shadow:inset 0 0 0 1px #efd0d0}.mode-tabs button span{font-size:14px;font-weight:700}.mode-tabs button b{color:var(--brand);font-size:17px}.mode-tabs button small{grid-column:1/-1;color:#6c788a;font-size:12px;line-height:1.45}
.md-editor{margin-top:14px;border:1px solid var(--line);border-radius:7px;overflow:hidden}.md-editor textarea,.result-source textarea{width:100%;padding:20px;border:0;outline:0;resize:vertical;background:#172033;color:#e5edf7;font:14px/1.75 Consolas,"Microsoft YaHei",monospace;tab-size:2}.md-editor textarea{min-height:360px}.tool-panel :deep(.el-alert){margin-top:16px}.result-grid{display:grid;grid-template-columns:1fr 1fr;border:1px solid var(--line);border-radius:7px;overflow:hidden}.result-grid>.full{grid-column:1/-1}.result-source{display:flex;min-width:0;flex-direction:column;border-right:1px solid var(--line)}.result-source header{height:44px;padding:0 14px;display:flex;align-items:center;justify-content:space-between;background:#f5f7fa;border-bottom:1px solid var(--line)}.result-source header span{color:var(--muted);font-size:12px}.result-source textarea{min-height:560px;flex:1}.warning-list{margin-top:16px;padding:15px 17px;display:flex;align-items:flex-start;gap:10px;color:#7c4a08;background:#fff8e8;border:1px solid #f3ddb0;border-radius:6px}.warning-list>.el-icon{margin-top:2px;font-size:19px}.warning-list p{margin:6px 0 0;font-size:13px;line-height:1.65}.processing{margin-right:auto;display:flex;align-items:center;gap:7px;color:var(--muted);font-size:13px}.side-panel{margin-bottom:16px;padding:20px}.side-panel h3{margin:0 0 16px;display:flex;align-items:center;gap:7px;font-size:17px}.side-panel dl{margin:0;display:grid;grid-template-columns:75px 1fr;gap:12px;font-size:14px}.side-panel dt{color:var(--muted)}.side-panel dd{margin:0;font-weight:700}.boundary-panel p{margin:10px 0;padding-left:13px;position:relative;color:#596679;font-size:14px;line-height:1.7}.boundary-panel p::before{content:'•';position:absolute;left:0;color:var(--brand)}
@media(max-width:700px){.mode-tabs{grid-template-columns:1fr}.mode-tabs button{min-height:78px}.result-grid{grid-template-columns:1fr}.result-source{border-right:0;border-bottom:1px solid var(--line)}.processing{margin:0 0 10px}}
</style>
