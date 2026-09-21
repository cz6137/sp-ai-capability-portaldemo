<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch, reactive, ref } from 'vue'
import { CircleCheck, Delete, Document, Download, Lock, Plus, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CapabilityManifestCard from '../components/CapabilityManifestCard.vue'
import ToolDocumentPreview from '../components/tools/ToolDocumentPreview.vue'
import ToolExperienceShell from '../components/tools/ToolExperienceShell.vue'
import ToolFileDropzone from '../components/tools/ToolFileDropzone.vue'
import ToolPanel from '../components/tools/ToolPanel.vue'
import { portalApi, type MeetingMinutesCapability, type MeetingMinutesJob, type MeetingMinutesResult } from '../api/portal'
import type { ToolDefinition } from '../capabilityManifest'

const props = withDefaults(defineProps<{ tool: ToolDefinition; preview?: boolean }>(), { preview: false })
const tool = props.tool
const selectedFile = ref<File>()
const uploadConfirmed = ref(false)
const resultConfirmed = ref(false)
const durationSeconds = ref(1)
const checking = ref(false)
const submitting = ref(false)
const capability = ref<MeetingMinutesCapability>()
const job = ref<MeetingMinutesJob>()
const result = ref<MeetingMinutesResult>()
const transcript = ref('')
const mode = ref<'transcript' | 'minutes'>('minutes')
const context = reactive({ subject: '', attendees: '', background: '', template: tool.trial.templates[0] || '通用会议' })
let pollTimer: number | undefined

const accepted = computed(() => (capability.value?.acceptedExtensions || tool.trial.acceptedExtensions).map(item => `.${item}`).join(','))
const maxFileMb = computed(() => capability.value?.maxFileSizeMb || tool.trial.maxFileSizeMB)
// `preview` describes where the capability manifest came from; it must not decide
// whether the separately configured runtime service can be used. The capability
// endpoint is the runtime source of truth for provider availability.
const ready = computed(() => (mode.value === 'transcript' ? capability.value?.transcriptionAvailable : capability.value?.minutesAvailable) === true)
const running = computed(() => !!job.value && !['COMPLETED', 'FAILED'].includes(job.value.status))
const canStart = computed(() => ready.value && uploadConfirmed.value && !!selectedFile.value && !running.value && !submitting.value)
const steps = tool.trial.steps
const activeStep = computed(() => {
  if (!job.value) return -1
  if (job.value.status === 'QUEUED') return 0
  if (job.value.status === 'TRANSCRIBING') return 1
  if (job.value.status === 'GENERATING') return 2
  return job.value.status === 'COMPLETED' ? 3 : -1
})
const meetingMarkdown = computed(() => {
  if (!result.value && !transcript.value) return ''
  const title = context.subject.trim() || selectedFile.value?.name.replace(/\.[^.]+$/, '') || '会议纪要'
  if (mode.value === 'transcript') return `# ${title}转写文本\n\n${transcript.value || '（无）'}\n`
  const meta = `会议主题：${title}　参会人员：${context.attendees || '未提供'}　生成日期：${new Date().toLocaleDateString('zh-CN')}`
  const value = result.value!
  return `# ${title}\n\n> ${meta}\n\n## 会议摘要\n\n${value.summary || '（无）'}\n${markdownSection('重点讨论事项', value.keypoints)}${markdownSection('关键决策', value.decisions)}${markdownSection('待办行动项', value.actions)}\n## 转写记录\n\n${transcript.value || '（无）'}\n`
})
const quality = computed(() => [
  { name: '材料', status: selectedFile.value ? '待确认' : '未检查', detail: selectedFile.value ? `已接收 ${selectedFile.value.name}，请确认录音完整且来源合规。` : '尚未选择录音。' },
  { name: '格式', status: transcript.value ? '待确认' : '未检查', detail: result.value ? '已形成摘要、讨论、决策、行动项和转写记录。' : transcript.value ? '已形成转写文本，需校对断句和专有名词。' : '处理后检查输出结构。' },
  { name: '内容', status: result.value ? '待确认' : '未检查', detail: result.value ? '必须核对人名、数字、日期、责任人与截止时间。' : '尚未生成内容。' },
  { name: '语境', status: context.subject || context.background ? '待确认' : '未补充', detail: context.subject || context.background ? '已提供会议上下文，仍需人工判断模型是否正确理解。' : '建议补充主题和背景以降低误判。' },
  { name: '安全合规', status: '需确认', detail: '录音会发送至平台配置的讯飞与模型服务；涉密、敏感会议不得使用在线模式。' },
])

async function loadCapabilities() {
  checking.value = true
  try { capability.value = await portalApi.meetingMinutesCapabilities() }
  catch {
    capability.value = { available: false, transcriptionAvailable: false, minutesAvailable: false, status: 'NOT_CONFIGURED', message: '当前平台接口未提供语音转写服务，真实录音不会被上传。', maxFileSizeMb: tool.trial.maxFileSizeMB, acceptedExtensions: tool.trial.acceptedExtensions, externalProviders: tool.trial.externalProviders, processingLocation: tool.trial.processingLocation, retentionPolicy: '待平台后端接入并配置后生效' }
  } finally { checking.value = false }
}

async function acceptFile(file: File) {
  if (running.value || submitting.value) { ElMessage.warning('当前任务处理中，请等待完成后更换录音'); return }
  uploadConfirmed.value = false
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  const allowed = (capability.value?.acceptedExtensions || tool.trial.acceptedExtensions).map(item => item.toLowerCase())
  if (!allowed.includes(extension)) { ElMessage.warning(`当前工具仅支持 ${accepted.value}`); return }
  if (file.size > maxFileMb.value * 1024 * 1024) { ElMessage.warning(`录音不能超过 ${maxFileMb.value} MB`); return }
  selectedFile.value = file
  durationSeconds.value = await detectDuration(file)
  context.subject ||= file.name.replace(/\.[^.]+$/, '')
  clearJob()
}
function detectDuration(file: File) {
  return new Promise<number>((resolve) => {
    const audio = document.createElement('audio'); const url = URL.createObjectURL(file)
    const finish = (value = 1) => { URL.revokeObjectURL(url); resolve(Math.max(1, Math.round(value || 1))) }
    audio.preload = 'metadata'; audio.onloadedmetadata = () => finish(audio.duration); audio.onerror = () => finish(); audio.src = url
  })
}
async function start() {
  if (!selectedFile.value || !canStart.value) return
  submitting.value = true; clearJob()
  try { job.value = await portalApi.startMeetingMinutesJob(selectedFile.value, { ...context, mode: mode.value, durationSeconds: durationSeconds.value }); schedulePoll(300) }
  catch (error) { ElMessage.error(apiMessage(error, '任务提交失败')) }
  finally { submitting.value = false }
}
function schedulePoll(delay = 2500) {
  if (!job.value?.id || ['COMPLETED', 'FAILED'].includes(job.value.status)) return
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(poll, delay)
}
async function poll() {
  if (!job.value?.id) return
  try {
    const current = await portalApi.meetingMinutesJob(job.value.id); job.value = current
    if (current.status === 'COMPLETED') { result.value = current.result ? JSON.parse(JSON.stringify(current.result)) : undefined; transcript.value = current.transcript || ''; ElMessage.success(current.mode === 'transcript' ? '语音转写已完成，请校对后导出' : '会议纪要已生成，请人工确认后导出') }
    else if (current.status === 'FAILED') ElMessage.error(current.error || '工具处理失败')
    else schedulePoll()
  } catch (error) { ElMessage.error(apiMessage(error, '任务状态读取失败')) }
}
watch([result, transcript, mode, () => context.subject, () => context.attendees], () => { resultConfirmed.value = false }, { deep: true, flush: 'sync' })
function clearJob() { resultConfirmed.value = false; if (pollTimer) window.clearTimeout(pollTimer); pollTimer = undefined; job.value = undefined; result.value = undefined; transcript.value = '' }
function addItem(key: 'keypoints' | 'decisions' | 'actions') { result.value?.[key].push('') }
function removeItem(key: 'keypoints' | 'decisions' | 'actions', index: number) { result.value?.[key].splice(index, 1) }
function markdownSection(name: string, values: string[]) { return values.filter(Boolean).length ? `\n## ${name}\n\n${values.filter(Boolean).map(item => `- ${item}`).join('\n')}\n` : '' }

function download(format: 'doc' | 'md') {
  if (!transcript.value || !resultConfirmed.value) return
  const title = context.subject.trim() || selectedFile.value?.name.replace(/\.[^.]+$/, '') || '会议纪要'
  if (format === 'md') { save(new Blob(['\ufeff' + meetingMarkdown.value], { type: 'text/markdown;charset=utf-8' }), `${title}.md`); return }
  const list = (name: string, values: string[]) => values.filter(Boolean).length ? `<h2>${name}</h2><ul>${values.filter(Boolean).map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>` : ''
  const minutes = result.value ? `<h2>会议摘要</h2><p>${escapeHtml(result.value.summary)}</p>${list('重点讨论事项', result.value.keypoints)}${list('关键决策', result.value.decisions)}${list('待办行动项', result.value.actions)}` : ''
  const html = `<html xmlns:w="urn:schemas-microsoft-com:office:word"><head><meta charset="utf-8"><style>body{font-family:"宋体",serif;font-size:12pt;line-height:1.8;max-width:900px;margin:24px auto}h1{font-size:22pt;color:#172033;border-bottom:3px solid #b52d2d;padding-bottom:10px}h2{font-size:15pt;margin-top:20pt;border-left:4px solid #b52d2d;padding-left:10px}p{white-space:pre-wrap}</style></head><body><h1>${escapeHtml(title)}</h1>${minutes}<h2>转写记录</h2><p>${escapeHtml(transcript.value)}</p></body></html>`
  save(new Blob(['\ufeff' + html], { type: 'application/msword;charset=utf-8' }), `${title}.doc`)
}
function printPdf() {
  if (!transcript.value || !resultConfirmed.value) return
  const popup = window.open('', '_blank'); if (!popup) { ElMessage.warning('请允许浏览器弹出打印窗口'); return }
  popup.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>会议纪要</title><style>body{font-family:"Microsoft YaHei",sans-serif;max-width:900px;margin:30px auto;line-height:1.9;padding:20px}</style></head><body>${document.querySelector('.meeting-preview .tool-document-page')?.innerHTML || ''}</body></html>`); popup.document.close(); popup.print()
}
function save(blob: Blob, name: string) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = name; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1500) }
function escapeHtml(value: string) { return String(value || '').replace(/[<>&"]/g, char => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' }[char] || char)) }
function apiMessage(error: unknown, fallback: string) { const value = error as { response?: { data?: { message?: string } }; message?: string }; return value.response?.data?.message || value.message || fallback }
function formatDuration(seconds: number) { return seconds >= 60 ? `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒` : `${seconds} 秒` }
onMounted(loadCapabilities)
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer) })
</script>

<template>
  <ToolExperienceShell :tool="tool" eyebrow="MEETING MINUTES" :banner-tone="ready ? 'ready' : 'blocked'" :banner-title="ready ? (props.preview ? '本地演示服务已接入。' : '真实处理服务已配置。') : '真实处理服务尚未就绪。'" :banner-text="capability?.message || '正在检查平台服务配置。'" :checking="checking" @refresh="loadCapabilities">
    <template v-if="props.preview && ready" #banner-action><span>本地演示接入</span></template>
    <ToolPanel step="01" title="提交会议录音" description="选择或拖入真实录音，再补充必要上下文；未连接服务时文件只停留在浏览器，不会上传。">
      <div class="mode-choice">
        <button :class="{ active: mode === 'transcript' }" type="button" :disabled="running" @click="mode = 'transcript'; clearJob()"><b>仅语音转文字</b><span>保留转写原文，不调用纪要模型</span></button>
        <button :class="{ active: mode === 'minutes' }" type="button" :disabled="running" @click="mode = 'minutes'; clearJob()"><b>转写并生成会议纪要</b><span>在转写后提取摘要、决策和行动项</span></button>
      </div>
      <ToolFileDropzone :file="selectedFile" :accept="accepted" :max-mb="maxFileMb" title="选择或拖入会议录音" @select="acceptFile" />
      <p v-if="selectedFile" class="file-duration">检测到录音时长约 {{ formatDuration(durationSeconds) }}</p>
      <div class="context-form">
        <label><span>会议主题</span><el-input v-model="context.subject" maxlength="120" placeholder="例如：项目需求评审会" /></label>
        <label v-if="mode === 'minutes'"><span>纪要模板</span><el-select v-model="context.template"><el-option v-for="item in tool.trial.templates" :key="item" :label="item" :value="item" /></el-select></label>
        <label v-if="mode === 'minutes'" class="full"><span>参会人员</span><el-input v-model="context.attendees" maxlength="300" placeholder="姓名或角色，使用顿号分隔" /></label>
        <label v-if="mode === 'minutes'" class="full"><span>项目背景</span><el-input v-model="context.background" type="textarea" :rows="4" maxlength="1200" show-word-limit placeholder="只填写帮助理解会议的事实背景，不要在此输入密钥或无关敏感信息" /></label>
      </div>
      <el-checkbox v-model="uploadConfirmed">我已确认录音允许发送至页面列明的处理服务，并知晓数据留存规则</el-checkbox>
      <template #footer><el-button class="start-button" type="primary" size="large" :icon="VideoPlay" :disabled="!canStart" :loading="submitting || running" @click="start">{{ ready ? (selectedFile ? (mode === 'transcript' ? '开始语音转文字' : '开始生成会议纪要') : '请先选择录音') : '当前模式的服务未配置' }}</el-button></template>
    </ToolPanel>

    <ToolPanel v-if="job" step="02" title="处理状态" :description="`${job.stage}。服务未提供准确完成比例，以下显示实际处理阶段。`">
      <div class="process-steps"><div v-for="(step,index) in steps" :key="step" :class="{ active: index <= activeStep, done: index < activeStep || job.status === 'COMPLETED' }"><i><el-icon v-if="index < activeStep || job.status === 'COMPLETED'"><CircleCheck /></el-icon><b v-else>{{ index + 1 }}</b></i><span>{{ step }}</span></div></div>
      <el-alert v-if="job.status === 'FAILED'" :title="job.error || '处理失败'" type="error" :closable="false" show-icon />
    </ToolPanel>

    <ToolPanel v-if="result || transcript" step="03" title="结果校对与导出" :description="mode === 'transcript' ? '对照录音校对断句、人名、数字和专有名词，确认后再导出。' : '左侧修改结构化结果，右侧同步显示统一 A4 预览；确认事实、人名、数字、决策和行动项后再导出。'">
      <div class="result-watermark">{{ tool.trial.resultWatermark }}</div>
      <div class="meeting-result-grid">
        <div class="meeting-editor">
          <template v-if="result">
            <label class="result-block"><b>会议摘要</b><el-input v-model="result.summary" type="textarea" :rows="5" /></label>
            <section v-for="group in [{ key: 'keypoints', label: '重点讨论事项' }, { key: 'decisions', label: '关键决策' }, { key: 'actions', label: '待办行动项' }]" :key="group.key" class="result-list"><header><b>{{ group.label }}</b><el-button text :icon="Plus" @click="addItem(group.key as 'keypoints' | 'decisions' | 'actions')">增加一项</el-button></header><div v-for="(_,index) in result[group.key as 'keypoints' | 'decisions' | 'actions']" :key="index"><span>{{ index + 1 }}</span><el-input v-model="result[group.key as 'keypoints' | 'decisions' | 'actions'][index]" /><el-button text type="danger" :icon="Delete" aria-label="删除" @click="removeItem(group.key as 'keypoints' | 'decisions' | 'actions', index)" /></div><p v-if="!result[group.key as 'keypoints' | 'decisions' | 'actions'].length">转写中未明确，可保留为空。</p></section>
          </template>
          <label class="result-block"><b>转写记录</b><el-input v-model="transcript" type="textarea" :rows="12" /></label>
        </div>
        <ToolDocumentPreview class="meeting-preview" :markdown="meetingMarkdown" />
      </div>
      <el-alert :title="mode === 'transcript' ? '语音识别可能误听人名、数字和专有名词；正式使用前必须对照录音校对。' : '责任人、截止日期、数字和最终决策必须人工确认；机器结果不能替代会议主持人签发。'" type="warning" :closable="false" show-icon />
      <el-checkbox v-model="resultConfirmed">{{ mode === 'transcript' ? '我已对照录音校对转写文本' : '我已核对转写、事实、责任人、日期与最终决策，确认本次导出内容' }}</el-checkbox>
      <template #footer><el-button :disabled="!resultConfirmed" :icon="Document" @click="download('doc')">下载 Word 兼容文件（.doc）</el-button><el-button :disabled="!resultConfirmed" :icon="Download" @click="download('md')">下载 Markdown</el-button><el-button :disabled="!resultConfirmed" @click="printPdf">打印 / 另存 PDF</el-button></template>
    </ToolPanel>

    <template #aside>
      <section class="surface side-panel status-panel"><h3><el-icon><Lock /></el-icon>接入与数据边界</h3><dl><dt>服务状态</dt><dd :class="ready ? 'ok' : 'warn'">{{ ready ? '已配置' : '未配置' }}</dd><dt>处理位置</dt><dd>{{ capability?.processingLocation || tool.trial.processingLocation }}</dd><dt>外部服务</dt><dd>{{ (capability?.externalProviders || tool.trial.externalProviders).join('、') || '无' }}</dd><dt>留存规则</dt><dd>{{ capability?.retentionPolicy || '待配置' }}</dd></dl></section>
      <CapabilityManifestCard :asset="tool" />
      <section class="surface side-panel quality-panel"><h3><el-icon><CircleCheck /></el-icon>五维人工质检</h3><div v-for="item in quality" :key="item.name"><header><b>{{ item.name }}</b><el-tag size="small" :type="item.status === '未检查' || item.status === '未补充' ? 'info' : 'warning'">{{ item.status }}</el-tag></header><p>{{ item.detail }}</p></div></section>
    </template>
  </ToolExperienceShell>
</template>

<style scoped>
.mode-choice{margin-bottom:18px;display:grid;grid-template-columns:1fr 1fr;gap:12px}.mode-choice button{padding:16px 18px;border:1px solid var(--line);border-radius:7px;display:flex;flex-direction:column;align-items:flex-start;gap:7px;background:#fff;color:#263349;cursor:pointer;text-align:left}.mode-choice button.active{border-color:#c66;background:#fff5f5;box-shadow:inset 4px 0 0 var(--brand)}.mode-choice b{font-size:16px}.mode-choice span{color:var(--muted);font-size:13px;line-height:1.55}
.file-duration{margin:8px 0 0;color:var(--muted);font-size:13px}.context-form{margin:22px 0 0;display:grid;grid-template-columns:minmax(0,1fr)240px;gap:16px}.context-form label{display:flex;flex-direction:column;gap:7px}.context-form label.full{grid-column:1/-1}.context-form label>span{font-size:15px;font-weight:700}.context-form .el-select{width:100%}.start-button{min-width:240px}.tool-panel :deep(.el-progress){margin:12px 0 24px}.process-steps{display:grid;grid-template-columns:repeat(4,1fr)}.process-steps div{position:relative;display:flex;flex-direction:column;align-items:center;gap:8px;color:#94a3b8;font-size:12px}.process-steps div::before{content:'';position:absolute;top:15px;right:50%;width:100%;height:2px;background:#e2e8f0}.process-steps div:first-child::before{display:none}.process-steps i{z-index:1;width:30px;height:30px;display:grid;place-items:center;border-radius:50%;background:#e2e8f0;font-style:normal}.process-steps .active{color:var(--brand)}.process-steps .active::before,.process-steps .active i{background:#e8b4b4}.process-steps .done i{color:#fff;background:var(--brand)}
.result-watermark{margin-bottom:18px;padding:10px 14px;color:#9a3412;background:#fff7ed;border:1px solid #fed7aa;border-radius:5px;font-size:13px;font-weight:700;text-align:center}.meeting-result-grid{display:grid;grid-template-columns:minmax(380px,.9fr) minmax(430px,1.1fr);border:1px solid var(--line);border-radius:7px;overflow:hidden}.meeting-editor{min-width:0;padding:20px;border-right:1px solid var(--line);background:#fff}.result-block{display:flex;flex-direction:column;gap:9px}.result-block>b,.result-list>header>b{font-size:16px}.result-block+.result-list,.result-list+.result-list,.result-list+.result-block{margin-top:22px}.result-list{padding-top:19px;border-top:1px solid var(--line)}.result-list>header{margin-bottom:11px;display:flex;align-items:center;justify-content:space-between}.result-list>div{margin:8px 0;display:grid;grid-template-columns:28px 1fr auto;gap:8px;align-items:center}.result-list>div>span{width:26px;height:26px;display:grid;place-items:center;border-radius:50%;color:var(--brand);background:var(--brand-soft);font-size:11px;font-weight:700}.result-list>p{color:var(--muted);font-size:13px}.tool-panel :deep(.el-alert){margin-top:20px}.side-panel{padding:20px;margin-bottom:16px}.side-panel h3{margin:0 0 15px;display:flex;align-items:center;gap:7px;font-size:17px}.side-panel dl{margin:0;display:grid;grid-template-columns:72px 1fr;gap:12px;font-size:14px}.side-panel dt{color:var(--muted)}.side-panel dd{margin:0;line-height:1.65;overflow-wrap:anywhere}.side-panel dd.ok{color:#15803d;font-weight:700}.side-panel dd.warn{color:#b45309;font-weight:700}.quality-panel>div{padding:12px 0;border-top:1px solid var(--line)}.quality-panel div header{display:flex;align-items:center;justify-content:space-between}.quality-panel p{margin:6px 0 0;color:var(--muted);font-size:13px;line-height:1.65}
@media(max-width:1180px){.meeting-result-grid{grid-template-columns:1fr}.meeting-editor{border-right:0;border-bottom:1px solid var(--line)}}@media(max-width:620px){.mode-choice{grid-template-columns:1fr}.context-form{grid-template-columns:1fr}.context-form label.full{grid-column:auto}.process-steps span{font-size:10px}.start-button{width:100%}}
</style>
