<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, reactive, ref, watch } from 'vue'
import { CircleCheck, Document, Download, Files, Plus, Refresh, UploadFilled, View, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CapabilityGovernancePanel from '../components/CapabilityGovernancePanel.vue'
import { capabilityStatusLabel, draftCapability, capabilityOperationError } from '../capabilityGovernance'
import CapabilityDetailFrame from '../components/CapabilityDetailFrame.vue'
import CapabilityIntakeHelp from '../components/CapabilityIntakeHelp.vue'
import ToolFileDropzone from '../components/tools/ToolFileDropzone.vue'
import { capabilityTemplate, validateCapabilityManifest, type CapabilityKindV2, type CapabilityManifestV2 } from '../capabilitySchema'
import { capabilityToLegacyTool, legacyToolToCapability, validateToolDefinition, type ToolDefinition } from '../capabilityManifest'
import { hasRegisteredToolExecutor, hasRegisteredServerJobHandler } from '../tools/runtime/registry'
import { registeredServerJobRenderer } from '../tools/runtime/rendererRegistry'
import { bundledCapabilities } from '../migratedCapabilities'
import { portalApi, type ManagedCapability } from '../api/portal'
import { PUBLIC_DEMO } from '../constants'
import {
  demoCapabilityAssets,
  loadDemoCapabilityState,
  saveDemoCapabilityDraft,
  transitionDemoCapability,
} from '../demoCapabilityGovernance'

const props = defineProps<{ employee?: boolean }>()
const demoMode = import.meta.env.DEV || PUBLIC_DEMO
const demoEmployeeName = '演示员工'
const DocumentTransformRenderer = defineAsyncComponent(() => import('../components/tools/DocumentTransformRenderer.vue'))
const active = ref<'assets' | 'intake' | 'edit' | 'preview' | 'runtime-preview' | 'governance'>('assets')
const manifest = ref<CapabilityManifestV2>(capabilityTemplate('skill'))
const packageFile = ref<File>()
const manifestFile = ref<File>()
const runtimeTool = ref<ToolDefinition>()
const intakeErrors = ref<string[]>([])
const intakeKind = ref<'capability' | 'runtime-tool'>('capability')
const parserExpanded = ref(false)
const templatePreviewKind = ref<CapabilityKindV2>('skill')
const packageInput = ref<HTMLInputElement>()
const manifestInput = ref<HTMLInputElement>()
const adminMain = ref<HTMLElement>()
const records = ref<ManagedCapability[]>([])
const loading = ref(false)
const recordTotal = ref(0)
const filters = reactive<{ status: string; kind: '' | CapabilityKindV2; submitter: string }>({ status: '', kind: '', submitter: '' })
const hasAdminFilters = computed(() => !props.employee && Boolean(filters.status || filters.kind || filters.submitter.trim()))
const submissionFlowActive = computed(() => ['intake', 'edit', 'preview', 'runtime-preview'].includes(active.value))
let loadSequence = 0
const saving = ref(false)
const backendAvailable = ref(false)
const editingId = ref('')
const governanceAsset = ref<ManagedCapability>()
const existingPackage = ref<{ slug: string; version: string; available: boolean; updatedAt?: string }>()
const ServerJobRenderer = computed(() => registeredServerJobRenderer(runtimeTool.value?.runtime.handler))
const text = reactive({ triggers: '', manual: '', faq: '', changelog: '', tags: '', audiences: '', scenarios: '', inputs: '', outputs: '', workflow: '', quickStart: '', quality: '', humanReview: '', boundaries: '', packageItems: '', extensions: '', providers: '' })

const errors = computed(() => validateCapabilityManifest(manifest.value))
const canReusePackage = computed(() => Boolean(existingPackage.value?.available && existingPackage.value.slug === manifest.value.identity.slug && existingPackage.value.version === manifest.value.identity.version))
const packageRequired = computed(() => (manifest.value.kind === 'skill' || manifest.value.delivery.mode !== 'online') && !canReusePackage.value)
const jsonText = computed(() => JSON.stringify(manifest.value, null, 2))
const blankTemplateJson = computed(() => JSON.stringify(capabilityTemplate(templatePreviewKind.value), null, 2))
const stats = computed(() => ({ total: records.value.length, skills: records.value.filter(item => item.kind === 'skill').length, tools: records.value.filter(item => item.kind === 'tool').length, review: records.value.reduce((total, item) => total + (item.pendingReviews ?? (item.status === 'IN_REVIEW' ? 1 : 0)), 0) }))
const intakeReady = computed(() => Boolean(manifestFile.value) && intakeErrors.value.length === 0)
const runtimeChecks = computed(() => {
  if (!runtimeTool.value) return []
  const checks = [
    { label: '统一 JSON 结构', ok: validateToolDefinition(runtimeTool.value).length === 0, detail: '身份、试用策略和 runtime 字段完整' },
    { label: '前台渲染器', ok: ['document-transform', 'custom'].includes(runtimeTool.value.runtime.renderer), detail: runtimeTool.value.runtime.renderer },
    { label: '处理执行器', ok: runtimeTool.value.runtime.renderer === 'custom' ? hasRegisteredServerJobHandler(runtimeTool.value.runtime.handler) : runtimeTool.value.runtime.operations.every(item => hasRegisteredToolExecutor(item.executor)), detail: runtimeTool.value.runtime.operations.map(item => item.executor).join('、') || '由独立后端流程执行' },
    { label: '数据边界', ok: Boolean(runtimeTool.value.runtime.securityStatement), detail: runtimeTool.value.runtime.securityStatement },
  ]
  return checks
})

function demoRecords(): ManagedCapability[] {
  const state = loadDemoCapabilityState(bundledCapabilities)
  let sources = demoCapabilityAssets(state)
  if (props.employee) {
    const mine = sources.filter(item => item.submitter === demoEmployeeName || item.createdBy === demoEmployeeName || item.createdBy === '当前员工').slice(0, 2)
    const selectedIds = new Set(mine.map(item => item.id))
    sources = mine.concat(sources.filter(item => !selectedIds.has(item.id)).slice(0, Math.max(0, 2 - mine.length))).map(item => ({ ...item, submitter: demoEmployeeName }))
  }
  return sources
    .filter(item => !filters.kind || item.kind === filters.kind)
    .filter(item => !filters.submitter.trim() || (item.submitter || '').includes(filters.submitter.trim()))
}

function displayedStatus(record: ManagedCapability) { return capabilityStatusLabel(record) }

function lines(value: string) { return value.split('\n').map(item => item.trim()).filter(Boolean) }
function rows(value: string) { return lines(value).map(line => line.split('|').map(item => item.trim())) }
function syncFromManifest() {
  const value = manifest.value
  text.triggers = value.usage.triggers?.join('\n') || ''; text.manual = value.references.manual || ''; text.faq = value.references.faq || ''; text.changelog = value.references.changelog || ''
  text.tags = value.classification.tags.join('\n'); text.audiences = value.classification.audiences.join('\n'); text.scenarios = value.usage.scenarios.join('\n')
  text.inputs = value.usage.inputs.map(item => [item.name, item.detail, item.formats?.join(' / ') || ''].join(' | ')).join('\n')
  text.outputs = value.usage.outputs.map(item => [item.name, item.detail, item.formats?.join(' / ') || ''].join(' | ')).join('\n')
  text.workflow = value.usage.workflow.map(item => `${item.title} | ${item.detail}`).join('\n'); text.quickStart = value.usage.quickStart.join('\n')
  text.quality = value.quality.dimensions.map(item => `${item.name} | ${item.criteria} | ${item.evidence}`).join('\n'); text.humanReview = value.quality.humanReview.join('\n'); text.boundaries = value.quality.boundaries.join('\n')
  text.packageItems = value.delivery.package?.packageItems.map(item => `${item.name} | ${item.detail}`).join('\n') || ''
  text.extensions = value.delivery.online?.acceptedExtensions.join('\n') || ''; text.providers = value.delivery.online?.externalProviders.join('\n') || ''
}
function syncToManifest() {
  const value = manifest.value
  value.usage.triggers = lines(text.triggers).length ? lines(text.triggers) : undefined
  value.references = { manual: text.manual.trim() || undefined, faq: text.faq.trim() || undefined, changelog: text.changelog.trim() || undefined }
  value.classification.tags = lines(text.tags); value.classification.audiences = lines(text.audiences); value.usage.scenarios = lines(text.scenarios)
  value.usage.inputs = rows(text.inputs).filter(item => item[0]).map(item => ({ name: item[0], detail: item[1] || '', formats: item[2] ? item[2].split('/').map(v => v.trim()).filter(Boolean) : undefined }))
  value.usage.outputs = rows(text.outputs).filter(item => item[0]).map(item => ({ name: item[0], detail: item[1] || '', formats: item[2] ? item[2].split('/').map(v => v.trim()).filter(Boolean) : undefined }))
  value.usage.workflow = rows(text.workflow).filter(item => item[0]).map(item => ({ title: item[0], detail: item[1] || '' })); value.usage.quickStart = lines(text.quickStart)
  value.quality.dimensions = rows(text.quality).filter(item => item[0]).map(item => ({ name: item[0], criteria: item[1] || '', evidence: item[2] || '' })); value.quality.humanReview = lines(text.humanReview); value.quality.boundaries = lines(text.boundaries)
  if (value.delivery.package) value.delivery.package.packageItems = rows(text.packageItems).filter(item => item[0]).map(item => ({ name: item[0], detail: item[1] || '' }))
  if (value.delivery.online) { value.delivery.online.acceptedExtensions = lines(text.extensions); value.delivery.online.externalProviders = lines(text.providers) }
}
function startNew(kind: CapabilityKindV2 = 'skill') { existingPackage.value = undefined; manifest.value = capabilityTemplate(kind); editingId.value = ''; packageFile.value = undefined; manifestFile.value = undefined; runtimeTool.value = undefined; syncFromManifest(); active.value = 'edit' }
function startUnifiedIntake() {
  if (!submissionFlowActive.value) {
    existingPackage.value = undefined; manifest.value = capabilityTemplate('skill'); editingId.value = ''
    packageFile.value = undefined; manifestFile.value = undefined; runtimeTool.value = undefined; intakeErrors.value = []
    syncFromManifest()
  }
  active.value = 'intake'
}
function switchKind(kind: CapabilityKindV2) {
  const previous = manifest.value; const next = capabilityTemplate(kind)
  next.identity = { ...previous.identity }; next.classification = JSON.parse(JSON.stringify(previous.classification)); next.usage = JSON.parse(JSON.stringify(previous.usage)); next.quality = JSON.parse(JSON.stringify(previous.quality)); next.governance = { ...previous.governance }; next.references = { ...previous.references }
  manifest.value = next; packageFile.value = undefined; syncFromManifest()
}
function onKindChange(value: string | number | boolean) { switchKind(value as CapabilityKindV2) }
function setDeliveryMode(value: string | number | boolean) {
  const mode = String(value) as 'package' | 'online' | 'hybrid'; const skillDefaults = capabilityTemplate('skill').delivery.package!; const toolDefaults = capabilityTemplate('tool').delivery.online!
  manifest.value.delivery.mode = mode
  manifest.value.delivery.package = mode === 'online' ? undefined : (manifest.value.delivery.package || skillDefaults)
  manifest.value.delivery.online = mode === 'package' ? undefined : (manifest.value.delivery.online || toolDefaults)
  syncFromManifest()
}
function editRecord(record: ManagedCapability, newVersion = false) {
  if (!newVersion && record.status !== 'DRAFT') { openGovernance(record); return }
  existingPackage.value = newVersion ? undefined : { slug: record.slug, version: record.version, available: Boolean(record.packageFileId), updatedAt: record.updatedAt }
  manifest.value = draftCapability(record.manifest, newVersion); editingId.value = record.id
  packageFile.value = undefined; manifestFile.value = undefined; runtimeTool.value = undefined
  syncFromManifest(); active.value = 'edit'
  if (newVersion) ElMessage.info('请填写新版本号和版本说明，并重新选择对应交付包')
}
function openGovernance(record: ManagedCapability) {
  if (demoMode) {
    manifest.value = JSON.parse(JSON.stringify(record.manifest))
    syncFromManifest()
    active.value = 'preview'
    return
  }
  governanceAsset.value = record
  active.value = 'governance'
}
async function unpublishRecord(record: ManagedCapability) {
  if (!record.currentlyPublished) return
  try {
    const { value } = await ElMessageBox.prompt('请填写下架原因。下架后前台不再展示该能力，但版本和操作记录仍会保留。', `下架「${record.name}」`, {
      confirmButtonText: '确认下架', cancelButtonText: '取消', inputPlaceholder: '例如：能力维护、服务调整或资料更新',
      inputValidator: value => Boolean(value?.trim()) || '请填写下架原因',
    })
    if (demoMode) {
      const state = loadDemoCapabilityState(bundledCapabilities)
      transitionDemoCapability(state, record.versionId, 'unpublish', '平台管理员', value.trim())
    } else {
      await portalApi.capabilityAction(record, 'unpublish', value.trim())
    }
    ElMessage.success('能力已下架，历史版本仍可追溯')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : capabilityOperationError(error))
  }
}
async function revealCurrentView() {
  await nextTick()
  adminMain.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
async function showPreview() {
  syncToManifest()
  active.value = 'preview'
  await revealCurrentView()
}
async function openEditor() {
  active.value = 'edit'
  await revealCurrentView()
}
async function startManualEditor() {
  startNew('skill')
  await revealCurrentView()
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  if (demoMode) {
    records.value = demoRecords()
    recordTotal.value = records.value.length
    backendAvailable.value = true
    loading.value = false
    return
  }
  try {
    const result = props.employee
      ? await portalApi.myCapabilities().then(items => ({ items, total: items.length }))
      : await portalApi.adminCapabilities({ status: filters.status || undefined, kind: filters.kind || undefined, submitter: filters.submitter.trim() || undefined })
    if (sequence !== loadSequence) return
    records.value = result.items
    recordTotal.value = result.total
    backendAvailable.value = true
  }
  catch (error) {
    if (sequence !== loadSequence) return
    backendAvailable.value = false; records.value = []; recordTotal.value = 0
    ElMessage.error(capabilityOperationError(error))
  }
  finally { if (sequence === loadSequence) loading.value = false }
}
function resetFilters() { filters.status = ''; filters.kind = ''; filters.submitter = ''; void load() }
function submittedAtLabel(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
function pickPackage(event: Event) { packageFile.value = (event.target as HTMLInputElement).files?.[0] || undefined }
function acceptPackage(file: File) { packageFile.value = file }
async function pickManifest(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return
  await acceptManifest(file)
  ;(event.target as HTMLInputElement).value = ''
}
async function acceptManifest(file: File) {
  manifestFile.value = file; packageFile.value = undefined; existingPackage.value = undefined; intakeErrors.value = []; runtimeTool.value = undefined
  parserExpanded.value = true
  try {
    const parsed = JSON.parse(await file.text())
    if (parsed?.identity) {
      const found = validateCapabilityManifest(parsed)
      intakeKind.value = parsed?.kind === 'tool' && parsed?.runtime ? 'runtime-tool' : 'capability'
      if (!found.length) {
        manifest.value = parsed as CapabilityManifestV2
        if (manifest.value.kind === 'tool' && manifest.value.runtime) {
          runtimeTool.value = capabilityToLegacyTool(manifest.value)
          if (manifest.value.runtime.renderer === 'document-transform') {
            found.push(...manifest.value.runtime.operations.filter(item => !hasRegisteredToolExecutor(item.executor)).map(item => `平台未注册执行器：${item.executor}`))
          }
        }
        editingId.value = ''; syncFromManifest()
      }
      intakeErrors.value = found
    } else if (parsed?.kind === 'tool' && parsed?.runtime) {
      const value = parsed as ToolDefinition
      const found = validateToolDefinition(value)
      const missing = value.runtime?.operations?.filter(item => !hasRegisteredToolExecutor(item.executor)).map(item => `平台未注册执行器：${item.executor}`) || []
      runtimeTool.value = value; intakeKind.value = 'runtime-tool'; intakeErrors.value = [...found, ...missing]
      if (found.length || missing.length) { active.value = 'intake'; return }
      const migrated = legacyToolToCapability(value)
      const migratedErrors = validateCapabilityManifest(migrated)
      intakeErrors.value.push(...migratedErrors)
      if (!migratedErrors.length) { manifest.value = migrated; editingId.value = ''; syncFromManifest() }
    } else {
      const found = validateCapabilityManifest(parsed)
      intakeKind.value = 'capability'; intakeErrors.value = found
      if (!found.length) { manifest.value = parsed; editingId.value = ''; syncFromManifest() }
    }
    active.value = 'intake'
      intakeErrors.value.length ? ElMessage.warning(`清单预检发现 ${intakeErrors.value.length} 项问题`) : ElMessage.success('清单结构校验通过，可以查看统一展示；包内文件仍需检查')
  } catch { intakeErrors.value = ['无法解析该 JSON 文件']; active.value = 'intake'; ElMessage.error('无法解析该 JSON 文件') }
}
async function openIntakePreview() {
  if (!intakeReady.value) return
  active.value = intakeKind.value === 'runtime-tool' ? 'runtime-preview' : 'preview'
  await revealCurrentView()
}
async function loadIntakeEditor() { if (!intakeReady.value) return; manifest.value = draftCapability(manifest.value); syncFromManifest(); active.value = 'edit'; await revealCurrentView() }
function downloadJson(value = manifest.value, fileName?: string) {
  syncToManifest(); const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/json;charset=utf-8' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = fileName || `${value.identity.slug || value.kind}-manifest.json`; link.click(); URL.revokeObjectURL(url)
}
function downloadTemplate(kind: CapabilityKindV2) { const value = capabilityTemplate(kind); downloadJson(value, `${kind}-manifest-template.json`) }
function changeTemplatePreview(value: string | number | boolean) { templatePreviewKind.value = value as CapabilityKindV2 }
function previewRuntime() {
  syncToManifest()
  const found = validateCapabilityManifest(manifest.value)
  if (found.length) { ElMessage.warning(found[0]); return }
  if (!manifest.value.runtime) { ElMessage.warning('该能力没有运行页面配置'); return }
  runtimeTool.value = capabilityToLegacyTool(manifest.value); active.value = 'runtime-preview'
}
async function save(submit = false) {
  syncToManifest(); manifest.value = draftCapability(manifest.value); const found = validateCapabilityManifest(manifest.value)
  if (found.length) { ElMessage.warning(found[0]); return }
  if (demoMode) {
    if (submit && !manifest.value.governance.changeNote?.trim()) { ElMessage.warning('提交审核前请填写版本说明'); return }
    saving.value = true
    try {
      const state = loadDemoCapabilityState(bundledCapabilities)
      const actor = props.employee ? demoEmployeeName : '平台管理员'
      const saved = saveDemoCapabilityDraft(state, manifest.value, actor)
      if (submit) transitionDemoCapability(state, saved.versionId, 'submit', actor, manifest.value.governance.changeNote || '提交审核')
      ElMessage.success(submit ? '已提交审核，可到审批工作台查看' : '草稿已保存')
      active.value = 'assets'
      await load()
    } catch (error) { ElMessage.error(error instanceof Error ? error.message : capabilityOperationError(error)) }
    finally { saving.value = false }
    return
  }
  if (packageRequired.value && !packageFile.value) { ElMessage.warning('当前版本缺少真实交付包；可以先导出清单，补齐包后再保存到后台'); return }
  saving.value = true
  try {
    const previous = existingPackage.value
    const expected = previous?.slug === manifest.value.identity.slug && previous.version === manifest.value.identity.version ? previous.updatedAt : undefined
    const saved = await (props.employee ? portalApi.importMyCapability : portalApi.importCapability)(manifest.value, packageFile.value, expected)
    if (submit) {
      await (props.employee ? portalApi.submitMyCapability(saved, manifest.value.governance.changeNote || '提交审核') : portalApi.capabilityAction(saved, 'submit', manifest.value.governance.changeNote || '提交审核'))
      ElMessage.success('已提交审核，可到审批工作台查看')
      active.value = 'assets'
      await load()
    } else {
      ElMessage.success('草稿已保存，可继续补充后再提交审核'); await load(); openGovernance(saved)
    }
  } catch (error) { ElMessage.error(capabilityOperationError(error)) }
  finally { saving.value = false }
}

syncFromManifest()
watch(() => Boolean(props.employee), () => {
  // Vue reuses this component when switching between the admin and employee
  // routes. Reset view-specific state and reload instead of leaving the two-row
  // employee snapshot under the administrator heading.
  active.value = 'assets'
  filters.status = ''
  filters.kind = ''
  filters.submitter = ''
  void load()
}, { immediate: true })
</script>

<template>
  <section class="admin-shell">
    <aside class="admin-nav">
      <div class="admin-brand"><b>{{ props.employee ? '我的能力提交' : '能力资产管理' }}</b><span>{{ props.employee ? '公司员工提交与跟踪' : '仅平台管理员可见' }}</span></div>
      <template v-if="props.employee">
        <button :class="{active:active==='assets' || active==='governance'}" @click="active='assets'"><el-icon><Files /></el-icon><span><b>我的提交</b><small>查看本人草稿与审核状态</small></span></button>
        <button :class="{active:active==='intake' || active==='runtime-preview'}" @click="startUnifiedIntake"><el-icon><UploadFilled /></el-icon><span><b>统一接入</b><small>上传、校验与识别</small></span></button>
        <button :class="{active:active==='edit'}" @click="active='edit'"><el-icon><Plus /></el-icon><span><b>清单编辑</b><small>完善能力与交付信息</small></span></button>
        <button :class="{active:active==='preview'}" @click="showPreview"><el-icon><View /></el-icon><span><b>详情展示</b><small>确认内容并提交审核</small></span></button>
        <div class="nav-note"><b>提交顺序</b><p>先用适配 Skill 整理原始资料，再上传清单和交付包；预检通过后保存草稿。</p></div>
      </template>
      <template v-else>
        <button :class="{active:active==='assets'}" @click="active='assets'"><el-icon><Files /></el-icon><span><b>资产清单</b><small>Skill 与工具统一管理</small></span></button>
        <button :class="{active:active==='intake'}" @click="active='intake'"><el-icon><UploadFilled /></el-icon><span><b>统一接入</b><small>上传、校验与识别</small></span></button>
        <button :class="{active:active==='edit'}" @click="active='edit'"><el-icon><Plus /></el-icon><span><b>清单编辑</b><small>Skill 与工具共同字段</small></span></button>
        <button :class="{active:active==='preview'}" @click="showPreview"><el-icon><View /></el-icon><span><b>详情展示</b><small>共用正式详情框架</small></span></button>
        <div class="nav-note"><b>统一原则</b><p>共同字段只维护一次；Skill 强制能力包信息，工具强制在线处理和数据边界。</p></div>
      </template>
    </aside>

    <main ref="adminMain" class="admin-main">
      <header class="page-title"><div><span>{{ props.employee ? 'EMPLOYEE · CAPABILITY SUBMISSIONS' : 'ADMIN · CAPABILITY ASSETS' }}</span><h1>{{ props.employee ? '我的能力提交' : 'Skill 与工具统一上架' }}</h1><p>{{ props.employee ? '查看本人提交状态，或按统一接入流程提交新的 Skill 与工具。' : '上传清单与交付包，在同一页面完成识别、校验、详情展示和管理。' }}</p></div><div><template v-if="props.employee"><el-button type="primary" :icon="UploadFilled" @click="startUnifiedIntake">提交新能力</el-button></template><template v-else><input ref="manifestInput" type="file" accept=".json,application/json" @change="pickManifest"><el-button :icon="UploadFilled" @click="manifestInput?.click()">导入 JSON</el-button><el-button type="primary" :icon="Plus" @click="startNew('skill')">新建能力</el-button></template></div></header>

      <CapabilityIntakeHelp v-if="active==='intake' || active==='edit'" />

      <template v-if="active==='assets'">
        <div class="stats"><div><span>{{ hasAdminFilters ? '筛选结果' : '全部资产' }}</span><b>{{ backendAvailable && !loading ? recordTotal : '—' }}</b></div><div><span>Skill</span><b>{{ backendAvailable && !loading ? stats.skills : '—' }}</b></div><div><span>工具</span><b>{{ backendAvailable && !loading ? stats.tools : '—' }}</b></div><div><span>待审核版本</span><b>{{ backendAvailable && !loading ? stats.review : '—' }}</b></div></div>
        <el-alert v-if="!demoMode && !backendAvailable && !loading" title="未能读取后台资产，请检查登录权限和服务连接。" type="warning" :closable="false" show-icon />
        <section class="surface asset-table"><header><div><h2>{{ props.employee ? '我的提交' : '统一资产清单' }}</h2><p>{{ props.employee ? '仅显示当前登录员工创建的能力及审核状态。' : '类型只影响交付配置，不再拆成两个后台。' }}</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></header>
          <el-form v-if="!props.employee" class="admin-filters" inline label-position="top" @submit.prevent="load">
            <el-form-item v-if="!demoMode" label="审核状态"><el-select v-model="filters.status" clearable placeholder="全部状态" @change="load"><el-option label="待审核" value="IN_REVIEW" /><el-option label="待上架（已通过）" value="APPROVED" /><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已归档" value="ARCHIVED" /></el-select></el-form-item>
            <el-form-item label="能力类型"><el-select v-model="filters.kind" clearable placeholder="全部类型" @change="load"><el-option label="Skill" value="skill" /><el-option label="工具" value="tool" /></el-select></el-form-item>
            <el-form-item label="提交人"><el-input v-model="filters.submitter" clearable placeholder="输入提交人" @clear="load" /></el-form-item>
            <el-form-item label="查询操作"><el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
          </el-form>
          <p v-if="!props.employee && backendAvailable" class="result-summary">共 {{ recordTotal }} 条，当前显示 {{ records.length }} 条；统计卡按当前列表计算。</p>
          <el-table v-loading="loading" :data="records" :empty-text="loading ? '正在读取' : '尚无能力资产'"><el-table-column label="类型" width="90"><template #default="{row}"><el-tag :type="row.kind==='skill'?'danger':'primary'">{{ row.kind==='skill'?'Skill':'工具' }}</el-tag></template></el-table-column><el-table-column prop="name" label="能力名称" min-width="230"/><el-table-column prop="slug" label="标识" min-width="180"/><el-table-column prop="version" label="版本" width="110"/><el-table-column label="状态" min-width="140"><template #default="{row}">{{ displayedStatus(row) }}</template></el-table-column><el-table-column prop="pendingReviews" label="待审核版本" width="110"/><el-table-column label="创建人" min-width="130"><template #default="{row}">{{ row.createdBy || '—' }}</template></el-table-column><el-table-column label="提交人" min-width="130"><template #default="{row}">{{ row.submitter || row.submitterId || row.createdBy || '—' }}</template></el-table-column><el-table-column label="提交时间" min-width="170"><template #default="{row}">{{ submittedAtLabel(row.submittedAt || row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="280" fixed="right"><template #default="{row}"><div class="asset-actions"><el-button link type="primary" @click="openGovernance(row)">{{ demoMode ? '查看详情' : '版本与审核' }}</el-button><el-button v-if="row.currentlyPublished" link type="primary" @click="editRecord(row, true)">新建版本</el-button><el-button v-if="!props.employee && row.currentlyPublished" link type="danger" @click="unpublishRecord(row)">下架</el-button></div></template></el-table-column></el-table></section>
      </template>

      <CapabilityGovernancePanel v-else-if="active==='governance' && governanceAsset" :key="governanceAsset.id" :asset-id="governanceAsset.id" :asset-name="governanceAsset.name" :employee="props.employee" @edit="editRecord" @changed="load" @back="active='assets'" />

      <template v-else-if="active==='intake'">
        <section class="surface intake-page">
          <header><div><h2>统一接入</h2><p>上传能力清单文件（capability.json）和交付包，校验通过后保存。</p></div></header>
          <div class="intake-grid">
            <div><h3>1. 上传能力清单</h3><ToolFileDropzone :file="manifestFile" accept=".json,application/json" :max-mb="2" title="选择或拖入能力清单文件（capability.json）" empty-text="由适应平台 Skill 生成；兼容 2.0 和旧 tool.json" @select="acceptManifest" /></div>
            <div><h3>2. 上传交付包</h3><ToolFileDropzone :file="packageFile" accept=".zip,.tar,.gz,.jar" :max-mb="500" title="选择或拖入能力包 / 工具部署包" empty-text="Skill 首次提交必须提供；纯在线工具可不提供" @select="acceptPackage" /></div>
          </div>
          <section class="manifest-parser">
            <header class="parser-heading"><div class="parser-title"><el-icon><Document /></el-icon><div><span>SCHEMA 2.1</span><h3>{{ manifestFile ? '能力清单解析结果' : '能力清单解析与校验' }}</h3><p>{{ manifestFile ? '已按平台统一字段读取能力清单。' : '未上传时提供空白模板，上传后显示解析和校验结果。' }}</p></div></div><div class="parser-controls"><div class="schema-actions"><el-button :icon="Download" @click="downloadTemplate('skill')">下载 Skill 清单模板</el-button><el-button :icon="Download" @click="downloadTemplate('tool')">下载工具清单模板</el-button></div><el-button text type="primary" @click="parserExpanded = !parserExpanded">{{ parserExpanded ? '收起' : '展开' }}</el-button></div></header>
            <div v-show="parserExpanded && !manifestFile" class="empty-template-preview"><header><div><b>空白能力清单模板</b><span>当前仅包含初始字段，不代表已经上传。</span></div><el-segmented :model-value="templatePreviewKind" :options="[{label:'Skill 模板',value:'skill'},{label:'工具模板',value:'tool'}]" @change="changeTemplatePreview" /></header><pre>{{ blankTemplateJson }}</pre></div>
            <template v-if="parserExpanded && manifestFile">
              <section :class="['intake-report', intakeErrors.length ? 'bad' : 'good']">
                <header><el-icon><WarningFilled v-if="intakeErrors.length" /><CircleCheck v-else /></el-icon><div><b>{{ intakeErrors.length ? '清单预检未通过' : '清单预检通过' }}</b><p>识别结果：{{ intakeKind === 'runtime-tool' ? `运行型工具 capability.json ${manifest.schemaVersion}` : `${manifest.kind === 'skill' ? 'Skill' : '工具'} capability.json ${manifest.schemaVersion}` }}</p></div></header>
                <ul v-if="intakeErrors.length"><li v-for="item in intakeErrors" :key="item">{{ item }}</li></ul>
                <template v-else>
                  <div class="manifest-summary"><div><span>能力名称</span><b>{{ manifest.identity.name }}</b></div><div><span>类型</span><b>{{ manifest.kind === 'skill' ? 'Skill' : '工具' }}</b></div><div><span>Schema</span><b>{{ manifest.schemaVersion }}</b></div><div><span>版本</span><b>v{{ manifest.identity.version }}</b></div><div><span>交付方式</span><b>{{ { package: '能力包', online: '在线使用', hybrid: '混合交付' }[manifest.delivery.mode] }}</b></div><div><span>字段摘要</span><b>{{ manifest.usage.inputs.length }} 项输入 · {{ manifest.usage.outputs.length }} 项输出 · {{ manifest.usage.workflow.length }} 个步骤</b></div></div>
                  <div v-if="runtimeTool" class="runtime-checks"><div v-for="item in runtimeChecks" :key="item.label"><el-icon><CircleCheck v-if="item.ok" /><WarningFilled v-else /></el-icon><span><b>{{ item.label }}</b><small>{{ item.detail }}</small></span></div></div>
                  <details class="json-preview"><summary>查看解析后的 capability.json</summary><pre>{{ jsonText }}</pre></details>
                </template>
                <div class="intake-actions"><el-button v-if="!intakeErrors.length" :icon="Download" @click="downloadJson()">导出解析结果</el-button><el-button :disabled="!intakeReady" @click="loadIntakeEditor">{{ props.employee ? '继续完善提交信息' : '载入统一登记表' }}</el-button><el-button type="primary" :icon="View" :disabled="!intakeReady" @click="openIntakePreview">{{ props.employee ? '预览提交效果' : '查看详情展示' }}</el-button></div>
              </section>
            </template>
          </section>
          <div class="intake-page-actions"><el-button type="primary" :icon="Plus" @click="startManualEditor">进入清单编辑</el-button></div>
        </section>
      </template>

      <template v-else-if="active==='edit'">
        <section class="editor-grid">
          <div class="surface form-panel">
            <header><div><h2>{{ props.employee ? '完善提交信息' : editingId ? '编辑能力资产' : '新增能力资产' }}</h2><p>登记顺序与正式详情一致；交付配置根据 Skill 或工具自动切换。</p></div><el-segmented :disabled="!!editingId" :model-value="manifest.kind" :options="[{label:'Skill',value:'skill'},{label:'工具',value:'tool'}]" @change="onKindChange"/></header>
            <el-form label-position="top">
              <h3 data-tone="identity">一、基本信息</h3><div class="two"><el-form-item label="能力名称" required><el-input v-model="manifest.identity.name"/></el-form-item><el-form-item label="英文标识" required><el-input v-model="manifest.identity.slug" :disabled="!!editingId" placeholder="lowercase-slug"/></el-form-item></div>
              <el-form-item label="一句话说明" required><el-input v-model="manifest.identity.tagline"/></el-form-item><el-form-item label="完整说明" required><el-input v-model="manifest.identity.description" type="textarea" :rows="3"/></el-form-item>
              <div class="three"><el-form-item label="版本"><el-input v-model="manifest.identity.version"/></el-form-item><el-form-item label="维护人"><el-input v-model="manifest.identity.maintainer"/></el-form-item><el-form-item label="更新时间"><el-date-picker v-model="manifest.identity.updatedAt" type="date" value-format="YYYY-MM-DD"/></el-form-item></div>
              <div class="two"><el-form-item label="标签（每行一个）"><el-input v-model="text.tags" type="textarea" :rows="3" @input="syncToManifest"/></el-form-item><el-form-item label="适用对象（每行一个）"><el-input v-model="text.audiences" type="textarea" :rows="3" @input="syncToManifest"/></el-form-item></div>
              <el-form-item label="适用项目阶段"><el-checkbox-group v-model="manifest.classification.stageIds"><el-checkbox v-for="id in 9" :key="id-1" :value="id-1">{{ id-1 }}</el-checkbox></el-checkbox-group></el-form-item>

              <h3 data-tone="use">二、适用任务与输入资料</h3><el-form-item label="触发词或开始方式（选填，每行一项）"><el-input v-model="text.triggers" type="textarea" :rows="2" @input="syncToManifest" /></el-form-item><el-form-item label="什么任务需要这个能力（每行一个）"><el-input v-model="text.scenarios" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item>
              <el-form-item label="输入资料（名称 | 说明 | 格式）"><el-input v-model="text.inputs" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item>

              <h3 data-tone="process">三、处理流程与确认节点</h3><el-form-item label="机器处理流程（步骤名 | 说明）"><el-input v-model="text.workflow" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item><el-form-item label="使用步骤（每行一步）"><el-input v-model="text.quickStart" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item>
              <div class="two"><el-form-item label="需要人工确认的节点（每行一个）"><el-input v-model="text.humanReview" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item><el-form-item label="机器执行约束（每行一个）"><el-input v-model="text.boundaries" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item></div>

              <h3 data-tone="output">四、输出成果与判断依据</h3><el-form-item label="输出成果（名称 | 说明 | 格式）"><el-input v-model="text.outputs" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item><el-form-item label="判断依据（维度 | 判断标准 | 应保留证据）"><el-input v-model="text.quality" type="textarea" :rows="4" @input="syncToManifest"/></el-form-item>

              <h3 data-tone="delivery">五、交付与使用资料</h3><el-form-item label="交付方式"><el-segmented :model-value="manifest.delivery.mode" :options="manifest.kind==='skill'?[{label:'能力包',value:'package'},{label:'混合交付',value:'hybrid'}]:[{label:'在线使用',value:'online'},{label:'混合交付',value:'hybrid'}]" @change="setDeliveryMode"/></el-form-item><template v-if="manifest.delivery.package"><div class="two"><el-form-item label="运行环境"><el-input v-model="manifest.delivery.package.environment"/></el-form-item><el-form-item label="安装方式"><el-input v-model="manifest.delivery.package.installGuide"/></el-form-item></div><el-form-item label="包内文件（名称 | 说明）"><el-input v-model="text.packageItems" type="textarea" :rows="3" @input="syncToManifest"/></el-form-item></template>
              <template v-if="manifest.delivery.online"><div class="two"><el-form-item label="在线服务状态"><el-switch v-model="manifest.delivery.online.enabled" active-text="已接通" inactive-text="未接通"/></el-form-item><el-form-item label="前台路由"><el-input v-model="manifest.delivery.online.route" placeholder="/tools/example"/></el-form-item></div><div class="two"><el-form-item label="文件格式（每行一个）"><el-input v-model="text.extensions" type="textarea" :rows="3" @input="syncToManifest"/></el-form-item><el-form-item label="外部服务（每行一个）"><el-input v-model="text.providers" type="textarea" :rows="3" @input="syncToManifest"/></el-form-item></div><el-form-item label="数据处理位置"><el-input v-model="manifest.delivery.online.processingLocation"/></el-form-item><el-form-item label="数据告知"><el-input v-model="manifest.delivery.online.dataNotice" type="textarea" :rows="2"/></el-form-item><el-form-item label="留存规则"><el-input v-model="manifest.delivery.online.retentionPolicy"/></el-form-item></template>

              <el-form-item label="使用手册位置"><el-input v-model="text.manual" @input="syncToManifest" placeholder="包内手册文件名或维护人提供的资料位置" /></el-form-item><div class="two"><el-form-item label="常见问题位置"><el-input v-model="text.faq" @input="syncToManifest" /></el-form-item><el-form-item label="版本记录位置"><el-input v-model="text.changelog" @input="syncToManifest" /></el-form-item></div><h3 data-tone="review">六、审核与文件</h3><el-alert :title="demoMode ? '修改会立即用于本次 Demo 展示，也可以随时导出清单。' : '此处只保存草稿。审核人和时间由后台实际操作生成；保存后到「版本与审核」提交、审核和发布。'" type="info" :closable="false" /><el-form-item label="版本说明"><el-input v-model="manifest.governance.changeNote"/></el-form-item>
              <el-form-item :label="manifest.kind==='skill'?'Skill 能力包':packageRequired?'工具交付包':'工具交付包（可选）'" :required="packageRequired"><div class="package-picker"><input ref="packageInput" type="file" accept=".zip,.tar,.gz,.jar" @change="pickPackage"><el-button :icon="UploadFilled" @click="packageInput?.click()">选择文件</el-button><span>{{ packageFile?.name || (canReusePackage ? '保留当前版本已上传的交付包' : '当前版本尚未选择交付包') }}</span></div></el-form-item>
            </el-form>
            <footer><div :class="['validation',errors.length?'bad':'good']">{{ errors.length ? `${errors.length} 项待完善：${errors[0]}` : '清单结构校验通过' }}</div><el-button :icon="Download" @click="downloadJson()">导出清单</el-button><el-button v-if="manifest.runtime" @click="previewRuntime">运行页展示</el-button><el-button type="primary" :icon="View" @click="showPreview">下一步：查看详情</el-button></footer>
          </div>
        </section>
      </template>

      <template v-else-if="active==='preview'">
        <div class="preview-toolbar"><div><b>能力详情确认</b><span>{{ errors.length ? `仍有 ${errors.length} 项需要完善，当前仅预览已填写内容` : '请确认展示内容，确认后可保存或提交审核' }}</span></div><el-button @click="openEditor">返回清单编辑</el-button></div>
        <el-alert v-if="errors.length" class="preview-warning" :title="`当前清单尚未通过校验：${errors[0]}`" type="warning" :closable="false" show-icon />
        <CapabilityDetailFrame :key="`${manifest.identity.slug}:${manifest.identity.version}`" :manifest="manifest" :preview="demoMode" />
        <footer class="preview-actions"><div :class="['validation',errors.length?'bad':'good']">{{ errors.length ? `${errors.length} 项待完善，返回清单编辑后再提交` : '清单结构校验通过，可以提交审核' }}</div><el-button @click="openEditor">返回清单编辑</el-button><el-button :loading="saving" :disabled="errors.length>0" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="saving" :disabled="errors.length>0" @click="save(true)">保存并提交审核</el-button></footer>
      </template>

      <template v-else-if="active==='runtime-preview'">
        <div class="preview-toolbar"><div><b>工具统一运行页展示</b><span>这里直接使用前台正式渲染器。</span></div><el-button @click="active='intake'">返回接入预检</el-button></div>
        <DocumentTransformRenderer v-if="runtimeTool?.runtime.renderer === 'document-transform'" :tool="runtimeTool" />
        <component :is="ServerJobRenderer" v-else-if="runtimeTool && ServerJobRenderer" :tool="runtimeTool" preview /><el-alert v-else title="当前工具没有已接入的运行页面" type="warning" :closable="false" />
      </template>

    </main>
  </section>
</template>

<style scoped>
.manifest-parser{margin:0 26px 26px;overflow:hidden;border:1px solid #dce2ea;background:#f8fafc}.parser-heading{padding:20px 22px;border-bottom:1px solid #dce2ea;display:flex;align-items:center;justify-content:space-between;gap:20px;background:#fff}.parser-title{min-width:0;display:flex;align-items:center;gap:14px}.parser-title>.el-icon{width:44px;height:44px;flex:none;border-radius:8px;display:grid;place-items:center;color:#ad2d2d;background:#f9eaea;font-size:23px}.parser-title span{color:#ad2d2d;font-size:11px;font-weight:800;letter-spacing:.12em}.parser-title h3{margin:4px 0 3px;font-size:20px}.parser-title p{margin:0;color:#68778b;font-size:14px}.parser-controls,.parser-heading .schema-actions{display:flex;align-items:center;gap:8px}.parser-controls{flex:none}.parser-heading .schema-actions{margin:0}.schema-rules{padding:20px 22px;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.schema-rules>div{padding:16px;border-left:3px solid #ad2d2d;background:#fff}.schema-rules b{font-size:15px}.schema-rules p{margin:7px 0 0;color:#627086;font-size:13px;line-height:1.65}.manifest-parser .intake-report{margin:0;border:0;background:transparent}.manifest-summary{margin-top:20px;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1px;border:1px solid rgba(22,101,52,.16);background:rgba(22,101,52,.14)}.manifest-summary>div{min-width:0;padding:14px;background:rgba(255,255,255,.84);display:flex;flex-direction:column;gap:6px}.manifest-summary span{color:#68778b;font-size:12px}.manifest-summary b{overflow-wrap:anywhere;color:#172033;font-size:14px}.json-preview{margin-top:18px;border:1px solid rgba(22,101,52,.18);background:#fff}.json-preview summary{padding:13px 15px;color:#35546a;font-size:14px;font-weight:700;cursor:pointer}.json-preview pre{max-height:420px;overflow:auto;margin:0;padding:18px;color:#d9e4f2;background:#172238;font-size:13px;line-height:1.65}.manifest-parser .intake-actions{padding-top:18px;border-top:1px solid rgba(100,116,139,.2)}
.admin-shell{min-height:calc(100vh - 80px);display:grid;grid-template-columns:280px minmax(0,1fr);background:#f2f4f7}.admin-nav{position:sticky;top:80px;height:calc(100vh - 80px);padding:26px 18px;overflow-y:auto;background:#172238;color:white}.admin-brand{padding:0 14px 24px;border-bottom:1px solid #334158;display:flex;flex-direction:column;gap:6px}.admin-brand b{font-size:22px}.admin-brand span{color:#aeb8c8;font-size:14px}.admin-nav>button{width:100%;padding:15px 14px;border:0;border-left:3px solid transparent;color:#c9d1dd;background:transparent;display:flex;align-items:flex-start;gap:12px;text-align:left;cursor:pointer}.admin-nav>button:hover,.admin-nav>button.active{color:white;background:#26364d;border-left-color:#d7423e}.admin-nav>button .el-icon{margin-top:3px;font-size:20px}.admin-nav>button span{display:flex;flex-direction:column;gap:4px}.admin-nav>button b{font-size:16px}.admin-nav>button small{color:#9da9ba;font-size:13px}.nav-note{margin-top:24px;padding:16px;border:1px solid #3b4960;background:#202e43}.nav-note p{margin:7px 0 0;color:#b9c3d1;font-size:14px;line-height:1.65}.admin-main{min-width:0;padding:32px 36px 60px;scroll-margin-top:80px}.page-title{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:24px}.page-title>div:last-child{display:flex;gap:10px}.page-title input,.package-picker input{display:none}.page-title span{color:#ad2d2d;font-size:14px;font-weight:800;letter-spacing:.12em}.page-title h1{margin:8px 0;font-size:34px}.page-title p{margin:0;color:#627086;font-size:17px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}.stats>div{padding:20px;border-top:4px solid #ad2d2d;background:white;display:flex;flex-direction:column;gap:8px}.stats span{color:#67758a;font-size:15px}.stats b{font-size:30px}.surface{box-sizing:border-box;width:100%;border:1px solid #dce2ea;background:white}.asset-table{margin-top:16px}.asset-table>header,.form-panel>header,.intake-page>header{padding:20px 24px;border-bottom:1px solid #dce2ea;display:flex;justify-content:space-between;align-items:center}.asset-table h2,.form-panel h2,.intake-page h2{margin:0;font-size:22px}.asset-table header p,.form-panel header p,.intake-page header p{margin:5px 0 0;color:#65748a;font-size:15px}.intake-grid{padding:26px;display:grid;grid-template-columns:1fr 1fr;gap:20px}.intake-grid h3{margin:0 0 12px;font-size:18px}.intake-report{margin:0 26px 26px;padding:22px;border:1px solid}.intake-report.good{color:#14532d;background:#f0fdf4;border-color:#bbf7d0}.intake-report.bad{color:#9a3412;background:#fff7ed;border-color:#fed7aa}.intake-report>header{display:flex;align-items:flex-start;gap:12px}.intake-report>header>.el-icon{margin-top:2px;font-size:24px}.intake-report>header b{font-size:18px}.intake-report>header p{margin:5px 0 0;color:inherit}.intake-report ul{margin:18px 0 0;line-height:1.8}.runtime-checks{margin-top:18px;display:grid;grid-template-columns:1fr 1fr;gap:10px}.runtime-checks>div{padding:13px;display:flex;align-items:flex-start;gap:9px;background:rgba(255,255,255,.72)}.runtime-checks span{display:flex;flex-direction:column;gap:5px}.runtime-checks small{line-height:1.5;overflow-wrap:anywhere}.intake-actions{margin-top:20px;display:flex;justify-content:flex-end;gap:10px}.form-panel :deep(.el-form){padding:26px}.form-panel h3{margin:28px 0 18px;padding:12px 16px;border-left:4px solid #ad2d2d;background:#f5f7fa;font-size:19px}.form-panel h3:first-child{margin-top:0}.two,.three{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.three{grid-template-columns:repeat(3,minmax(0,1fr))}.form-panel :deep(.el-form-item__label){font-size:15px;font-weight:700}.form-panel :deep(.el-input__wrapper),.form-panel :deep(.el-textarea__inner){font-size:15px}.package-picker{box-sizing:border-box;width:100%;padding:14px;border:1px dashed #bcc6d4;background:#f8fafc;display:flex;align-items:center;gap:12px}.package-picker span{color:#5f6e82}.form-panel>footer{position:sticky;bottom:0;z-index:3;padding:16px 24px;border-top:1px solid #dce2ea;background:rgba(255,255,255,.97);display:flex;align-items:center;justify-content:flex-end;gap:10px}.validation{margin-right:auto;font-size:14px}.validation.bad{color:#a33}.validation.good{color:#16846e}.preview-toolbar{padding:16px 20px;border:1px solid #dce2ea;border-bottom:0;background:white;display:flex;justify-content:space-between;align-items:center}.preview-toolbar>div{display:flex;flex-direction:column;gap:4px}.preview-toolbar b{font-size:18px}.preview-toolbar span{color:#66758a}.preview-warning{margin-bottom:14px}.schema-page{padding:32px}.schema-page h2{margin-top:0;font-size:28px}.schema-page>p{font-size:17px;line-height:1.75;color:#536278}.schema-actions{display:flex;gap:10px;margin:24px 0}.rules{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.rules>div{padding:20px;border-top:4px solid #ad2d2d;background:#f6f8fa}.rules b{font-size:18px}.rules p{margin:8px 0 0;color:#526176;font-size:15px;line-height:1.7}.schema-page pre{max-height:560px;overflow:auto;margin:24px 0 0;padding:24px;color:#d9e4f2;background:#172238;font-size:14px;line-height:1.65}
@media(max-width:1050px){.admin-shell{grid-template-columns:220px 1fr}.admin-main{padding:26px 22px}.three,.intake-grid{grid-template-columns:1fr}.rules{grid-template-columns:1fr}.page-title{align-items:flex-start;flex-direction:column}}@media(max-width:760px){.admin-shell{display:block}.admin-nav{position:static;height:auto;display:grid;grid-template-columns:1fr 1fr}.admin-brand{grid-column:1/-1}.nav-note{display:none}.admin-main{padding:22px 12px}.stats{grid-template-columns:1fr 1fr}.two,.three,.runtime-checks{grid-template-columns:1fr}.page-title>div:last-child,.schema-actions{flex-wrap:wrap}.form-panel>footer,.intake-actions{flex-wrap:wrap}.validation{width:100%;margin:0}}
.admin-filters{padding:16px 24px 0;display:flex;align-items:flex-end;gap:12px;flex-wrap:wrap}.admin-filters .el-form-item{margin-bottom:12px}.admin-filters .el-select{width:180px}.admin-filters .el-input{width:200px}.result-summary{margin:0;padding:0 24px 12px;color:#67758a;font-size:13px}
.asset-actions{display:flex;align-items:center;gap:12px;white-space:nowrap}.asset-actions :deep(.el-button){margin-left:0}
.form-panel h3[data-tone="identity"]{border-left-color:#a83b3b;background:#faf4f4}.form-panel h3[data-tone="use"]{border-left-color:#3f648b;background:#f2f7fb}.form-panel h3[data-tone="process"]{border-left-color:#625b96;background:#f5f4fa}.form-panel h3[data-tone="output"]{border-left-color:#39725b;background:#f1f7f4}.form-panel h3[data-tone="delivery"]{border-left-color:#9a6728;background:#fbf7ef}.form-panel h3[data-tone="review"]{border-left-color:#58677b;background:#f3f5f7}
@media(max-width:1050px){.parser-heading{align-items:flex-start;flex-direction:column}.schema-rules,.manifest-summary{grid-template-columns:1fr}.manifest-parser{margin:0 20px 22px}.parser-controls{width:100%;justify-content:space-between}}@media(max-width:760px){.manifest-parser{margin:0 12px 18px}.parser-heading,.schema-rules{padding:16px}.parser-controls{align-items:flex-start;flex-direction:column}.parser-heading .schema-actions{width:100%;flex-wrap:wrap}}
.empty-template-preview{padding:20px 22px}.empty-template-preview>header{margin-bottom:12px;display:flex;align-items:center;justify-content:space-between;gap:16px}.empty-template-preview>header>div{display:flex;flex-direction:column;gap:4px}.empty-template-preview>header b{font-size:16px}.empty-template-preview>header span{color:#68778b;font-size:13px}.empty-template-preview pre{max-height:420px;overflow:auto;margin:0;padding:18px;color:#d9e4f2;background:#172238;font-size:13px;line-height:1.65}.intake-page-actions{margin:0 26px 26px;display:flex;justify-content:flex-end}.preview-actions{position:fixed;right:36px;bottom:24px;z-index:20;padding:14px 16px;border:1px solid #dce2ea;border-radius:8px;background:rgba(255,255,255,.98);box-shadow:0 10px 32px rgba(23,32,51,.18);display:flex;align-items:center;justify-content:flex-end;gap:10px}.preview-actions .validation{max-width:280px}
@media(max-width:1050px){.intake-page-actions{margin:0 20px 22px}}@media(max-width:760px){.intake-page-actions{margin:0 12px 18px}.preview-actions{right:12px;bottom:12px;left:12px;flex-wrap:wrap}.preview-actions .validation{width:100%;max-width:none;margin:0}}
</style>
