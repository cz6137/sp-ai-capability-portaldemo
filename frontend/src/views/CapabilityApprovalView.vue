<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Document, Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { capabilityActionLabels, capabilityOperationError } from '../capabilityGovernance'
import { portalApi, type CapabilityAction, type ManagedCapability } from '../api/portal'
import type { Asset } from '../types'
import {
  assetUpdatedAt,
  buildDemoHistory,
  historyEvents as buildHistoryEvents,
  historyForAsset,
  versionUpdatedAt,
  type DemoLifecycleStatus,
  type HistoryRecord,
  type HistoryStep,
  type HistoryVersion,
} from '../data/historyDemo'
import { bundledCapabilities } from '../migratedCapabilities'
import { PUBLIC_DEMO } from '../constants'
import {
  loadDemoCapabilityState,
  transitionDemoCapability,
  type DemoManagedCapability,
} from '../demoCapabilityGovernance'

type DeliverableKind = 'skill' | 'tool' | 'knowledge'

interface WorkbenchRow {
  key: string
  /** 行主标题：能力名称，知识库 demo 下是材料名称。 */
  title: string
  /** 行副标题：标识与版本。 */
  subtitle: string
  /** 能力标识，用于匹配历史记录。 */
  slug: string
  /** 能力版本号，用于回退生成历史记录。 */
  versionName: string
  /** 前台当前使用的版本；审批中的 versionName 可以是另一个新版本。 */
  publishedVersionName?: string
  tagLabel: string
  kind: DeliverableKind
  submitterId: string
  submittedAt: string
  updatedAt: string
  packageAvailable: boolean
  tag: string
  tagType: 'info' | 'primary' | 'warning' | 'success' | 'danger'
  actions: CapabilityAction[]
  lifecycleStatus: DemoLifecycleStatus
  /** 预设演示数据：操作不可真正执行。 */
  demo?: boolean
  asset?: ManagedCapability
  version?: ManagedCapability
}

const kinds: Array<{ key: DeliverableKind; label: string; detail: string }> = [
  { key: 'skill', label: 'Skill', detail: '按审批状态处理 Skill 类交付物的版本。' },
  { key: 'tool', label: '工具', detail: '按审批状态处理工具类交付物的版本。' },
  { key: 'knowledge', label: '知识库', detail: '知识库取自交付基线库材料，可体验完整审批状态流转。' },
]

/** 知识库审批状态预设：后端接入前用它把知识库材料放进同一套状态分组。 */
const knowledgeStatuses = ['IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED'] as const
const knowledgeSubmitters = ['演示员工', '演示工具组', '演示平台组'] as const
/** 知识库材料共 300 余项，演示只取前若干项，避免页面过长。 */
const knowledgeDemoLimit = 60

const demoMode = import.meta.env.DEV || PUBLIC_DEMO

/** 管理员只看员工已经提交的版本；草稿是员工自己的事，不进审批队列。 */
const keys = [
  { key: 'IN_REVIEW', name: '待审核', detail: '已提交，等待独立管理员审核通过或退回修改。', empty: '暂无待审核版本。' },
  { key: 'APPROVED', name: '审核通过，待发布', detail: '已由独立管理员审核通过，确认交付包后即可发布到前台。', empty: '暂无待发布版本。' },
  { key: 'PUBLISHED', name: '当前发布版本', detail: '正在前台供员工使用，可继续提交后续版本而不影响当前版本。', empty: '暂无当前发布版本。' },
  { key: 'ARCHIVED', name: '已归档版本', detail: '本次版本升级已结束，能力仍保留另一个当前发布版本。', empty: '暂无已归档版本。' },
  { key: 'DELISTED', name: '已下架能力', detail: '能力已停止对外提供，历史版本仍保留供审计和追溯。', empty: '暂无已下架能力。' },
  { key: 'REJECTED', name: '已退回', detail: '审核未通过，已退回提交人修改，不进入发布环节。', empty: '暂无已退回版本。' },
] as const

const rows = ref<WorkbenchRow[]>([])
const loading = ref(false)
const failure = ref('')
const busyKey = ref('')
const dialogKey = ref('')
const reason = ref('')
const knowledgeAssets = ref<Asset[]>([])
const kind = ref<DeliverableKind>('skill')
/** 历史记录抽屉：当前查看的行与选中的版本。 */
const historyKey = ref('')
const historyVersion = ref('')

const activeKind = computed(() => kinds.find(item => item.key === kind.value) ?? kinds[0])
const groups = computed(() => keys.map(group => ({ ...group, items: rows.value.filter(row => row.tag === group.key) })))
const versionTotal = computed(() => rows.value.length)
const reviewTotal = computed(() => rows.value.filter(row => row.tag === 'IN_REVIEW').length)
const approvedTotal = computed(() => rows.value.filter(row => row.tag === 'APPROVED').length)
const publishedTotal = computed(() => rows.value.filter(row => row.tag === 'PUBLISHED').length)
const rejectedTotal = computed(() => rows.value.filter(row => row.tag === 'REJECTED').length)
const target = computed(() => rows.value.find(row => row.key === dialogKey.value))
const dialogBusy = computed(() => Boolean(dialogKey.value) && busyKey.value === dialogKey.value)
const dialogActions = computed<CapabilityAction[]>(() => target.value?.actions ?? [])
/** 抽屉里的历史记录。示例数据，见 data/historyDemo.ts。 */
const historyRow = computed(() => rows.value.find(row => row.key === historyKey.value))
const historyRecord = computed(() => {
  const row = historyRow.value
  if (!row || loading.value) return undefined
  if (row.demo && row.kind !== 'knowledge') return demoAssetHistory(row)
  if (row.demo) return buildDemoHistory({
    slug: row.slug,
    kind: row.kind,
    name: row.title,
    version: row.versionName,
    publishedVersion: row.publishedVersionName,
    status: row.lifecycleStatus,
    submitter: row.submitterId,
  })
  return historyForAsset(row.slug)
})
/** 真实后端尚未返回任何版本历史的条目。 */
const historyEmpty = computed(() => Boolean(historyRow.value) && !loading.value && historyRecord.value === undefined)
const historyEvents = computed(() => historyRecord.value ? buildHistoryEvents(historyRecord.value) : [])
const historyScoped = computed(() => historyEvents.value.filter(event => event.version === historyVersion.value))
const historySubtitle = computed(() => {
  const row = historyRow.value
  const record = historyRecord.value
  if (!row) return ''
  if (!record) return `${kindLabel(row.kind)} · ${row.slug} · 尚无历史记录`
  return `${kindLabel(row.kind)} · ${row.slug} · 共 ${record.versions.length} 个版本 · 最近更新 ${assetUpdatedAt(record)}`
})

function statusTagOf(status: string, published: boolean): { tag: WorkbenchRow['tag']; type: WorkbenchRow['tagType'] } {
  const tag = status
  return {
    tag,
    type: tag === 'PUBLISHED' ? 'primary' : tag === 'APPROVED' ? 'success' : tag === 'IN_REVIEW' ? 'warning' : tag === 'REJECTED' ? 'danger' : 'info',
  }
}

/**
 * 演示环境也按资产聚合版本。无论用户从待审核、待发布或当前发布区打开，
 * 同一 slug 都返回同一份历史，避免每一行各自合成一套互相矛盾的版本。
 */
function demoAssetHistory(row: WorkbenchRow): HistoryRecord {
  const state = loadDemoCapabilityState(bundledCapabilities)
  const records = state.records
    .filter(item => item.slug === row.slug && item.demoLifecycle !== 'DRAFT')
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  const publishedVersion = records.find(item => item.currentlyPublished)?.version
  const versions = records.map(record => {
    const generated = buildDemoHistory({
      slug: record.slug,
      kind: record.kind,
      name: record.name,
      version: record.version,
      publishedVersion,
      status: lifecycleOf(record),
      submitter: record.submitter || record.submitterId || record.createdBy || '—',
    })
    const version = generated.versions.find(item => item.version === record.version) || generated.versions[0]
    return alignDemoVersionTime({
      ...version,
      status: record.status,
      assetStatus: record.assetStatus,
      current: record.currentlyPublished,
    }, record.updatedAt)
  })
  return { slug: row.slug, kind: row.kind, name: row.title, versions }
}

/** 将生成的操作轨迹对齐到版本自己的更新时间，保证列表时间和抽屉时间一致。 */
function alignDemoVersionTime(version: HistoryVersion, updatedAt: string): HistoryVersion {
  const updated = new Date(updatedAt)
  if (Number.isNaN(updated.getTime()) || !version.steps.length) return version
  const base = new Date('2026-09-14T00:00:00+08:00')
  const updatedDay = new Date(updated.getFullYear(), updated.getMonth(), updated.getDate())
  const baseDay = new Date(base.getFullYear(), base.getMonth(), base.getDate())
  const finalOffset = Math.round((baseDay.getTime() - updatedDay.getTime()) / 86_400_000)
  const finalTime = updated.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  const steps = version.steps.map((step, index) => ({
    ...step,
    dayOffset: finalOffset + version.steps.length - index - 1,
    time: index === version.steps.length - 1 ? finalTime : step.time,
  }))
  return { ...version, steps }
}

/** 知识库审批状态是预设的演示数据：同一状态沿用与能力版本一致的分组和操作，但不会真正执行。 */
function demoRow(asset: Asset, index: number): WorkbenchRow {
  const status = knowledgeStatuses[index % knowledgeStatuses.length]
  const published = status === 'PUBLISHED'
  const queue = statusTagOf(status, published)
  return {
    key: `knowledge-${asset.stage_num}-${asset.sub_number}-${index}`,
    title: asset.name,
    subtitle: `${asset.stage_name} / ${asset.sub_name} · S${asset.stage_num}.${asset.sub_number}`,
    tagLabel: statusLabelOf(status, published),
    kind: 'knowledge',
    slug: asset.name,
    versionName: `S${asset.stage_num}.${asset.sub_number}`,
    submitterId: knowledgeSubmitters[index % knowledgeSubmitters.length],
    submittedAt: '2026-09-12T10:00:00+08:00',
    updatedAt: '2026-09-14T15:00:00+08:00',
    packageAvailable: false,
    tag: queue.tag,
    tagType: queue.type,
    lifecycleStatus: status,
    actions: statusActionsOf(status, status === 'APPROVED', published),
    demo: true,
  }
}

function statusLabelOf(status: string, published: boolean): string {
  if (status === 'IN_REVIEW') return '待审核'
  if (status === 'APPROVED') return '审核通过，待发布'
  if (status === 'PUBLISHED') return published ? '当前发布' : '历史发布'
  if (status === 'ARCHIVED') return '已归档'
  if (status === 'DELISTED') return '已下架'
  if (status === 'REJECTED') return '已退回'
  return '草稿'
}

/** 起草中的版本由员工自己提交，管理员不提供提交或退回草稿的操作。 */
function statusActionsOf(status: string, approvedByReview: boolean, published: boolean): CapabilityAction[] {
  if (status === 'IN_REVIEW') return ['approve', 'reject']
  if (status === 'APPROVED' && approvedByReview) return ['publish']
  if (status === 'PUBLISHED') return published ? ['unpublish'] : []
  if (status === 'DELISTED') return ['archive']
  return []
}

async function loadKnowledge() {
  const { assets } = await import('../data')
  knowledgeAssets.value = [...assets]
  // 保持按交付阶段排列，便于对照基线库目录；状态分组由预设状态决定。
  rows.value = [...assets]
    .sort((left, right) => left.stage_num - right.stage_num || left.sub_number.localeCompare(right.sub_number) || left.name.localeCompare(right.name))
    .slice(0, knowledgeDemoLimit)
    .map((asset, index) => demoRow(asset, index))
  return assets.length
}

function lifecycleOf(record: ManagedCapability | DemoManagedCapability): DemoLifecycleStatus {
  const demoLifecycle = (record as DemoManagedCapability).demoLifecycle
  if (demoLifecycle) return demoLifecycle === 'DRAFT' ? 'REJECTED' : demoLifecycle
  if (record.currentlyPublished) return 'PUBLISHED'
  if (record.status === 'IN_REVIEW') return record.reviewApproved ? 'APPROVED' : 'IN_REVIEW'
  if (record.status === 'ARCHIVED') return 'ARCHIVED'
  if (record.status === 'PUBLISHED' && record.assetStatus === 'ARCHIVED') return 'DELISTED'
  return record.status === 'DRAFT' ? 'REJECTED' : 'ARCHIVED'
}

function capabilityRow(record: ManagedCapability | DemoManagedCapability, allVersions: Array<ManagedCapability | DemoManagedCapability>, isDemo: boolean): WorkbenchRow {
  const status = lifecycleOf(record)
  const published = status === 'PUBLISHED' && record.currentlyPublished
  const publishedVersion = allVersions.find(item => item.slug === record.slug && item.currentlyPublished)?.version
  const queue = statusTagOf(status, published)
  return {
    key: record.versionId,
    title: record.name,
    subtitle: published
      ? `${record.slug} · 当前发布 v${record.version}`
      : `${record.slug} · 提交 v${record.version}${publishedVersion ? ` · 前台 v${publishedVersion}` : ' · 当前未上架'}`,
    tagLabel: statusLabelOf(status, published),
    kind: record.kind,
    slug: record.slug,
    versionName: record.version,
    publishedVersionName: publishedVersion,
    submitterId: record.submitter || record.submitterId || record.createdBy || '—',
    submittedAt: record.submittedAt || record.createdAt,
    updatedAt: record.updatedAt,
    packageAvailable: Boolean(record.packageFileId),
    tag: queue.tag,
    tagType: queue.type,
    lifecycleStatus: status,
    actions: statusActionsOf(status, record.reviewApproved, published),
    demo: isDemo,
    asset: record,
    version: record,
  }
}

async function loadCapabilities(current: 'skill' | 'tool') {
  if (demoMode) {
    const state = loadDemoCapabilityState(bundledCapabilities)
    const versions = state.records.filter(record => record.kind === current && record.demoLifecycle !== 'DRAFT')
    rows.value = versions.map(record => capabilityRow(record, state.records, true))
    return
  }
  const result = await portalApi.adminCapabilities({ kind: current })
  const versions = result.items.filter(record => record.status !== 'DRAFT')
  rows.value = versions.map(record => capabilityRow(record, result.items, false))
}

async function load() {
  loading.value = true
  failure.value = ''
  dialogKey.value = ''
  try {
    if (kind.value === 'knowledge') {
      // 知识库不查询后端：材料来自交付基线库，审批状态是预设演示值。
      const total = await loadKnowledge()
      if (total === 0) failure.value = '尚未读取到交付基线库材料，知识库内容为空。'
      return
    }
    await loadCapabilities(kind.value)
    return
  } catch (error) {
    rows.value = []
    failure.value = capabilityOperationError(error)
  } finally {
    loading.value = false
  }
}

async function switchKind(next: DeliverableKind) {
  if (kind.value === next) return
  kind.value = next
  rows.value = []
  closeHistory()
  await load()
}

function openHistory(row: WorkbenchRow) {
  historyKey.value = row.key
  historyVersion.value = row.versionName
}
function closeHistory() {
  historyKey.value = ''
  historyVersion.value = ''
}
/** 版本项按版本自己的真实状态取标签；「待审核」条目里正在公开的旧版本仍显示「当前公开」。 */
function historyVersionLabel(version: { status: string; assetStatus: string; current: boolean; steps?: HistoryStep[] }): { text: string; type: 'success' | 'primary' | 'warning' | 'info' } {
  if (version.status === 'PUBLISHED' && version.assetStatus === 'PUBLISHED') {
    return version.current ? { text: '当前公开', type: 'primary' } : { text: '历史发布', type: 'primary' }
  }
  if (version.status === 'PUBLISHED') return { text: '已下架', type: 'info' }
  if (version.status === 'IN_REVIEW' && version.steps?.some(step => step.action === 'CAPABILITY_APPROVE')) return { text: '审核通过，待发布', type: 'success' }
  if (version.status === 'IN_REVIEW') return { text: '待审核', type: 'warning' }
  if (version.status === 'DRAFT') return { text: '草稿', type: 'info' }
  return { text: '已归档', type: 'info' }
}

function ask(row: WorkbenchRow) {
  dialogKey.value = row.key
  reason.value = ''
}

async function act(action: CapabilityAction, row: WorkbenchRow) {
  const feedback = reason.value.trim()
  if (!feedback) return
  busyKey.value = row.key
  failure.value = ''
  try {
    if (row.demo) {
      if (row.kind === 'knowledge') {
        const status: DemoLifecycleStatus = action === 'approve' ? 'APPROVED' : action === 'reject' ? 'REJECTED' : action === 'publish' ? 'PUBLISHED' : action === 'unpublish' ? 'DELISTED' : 'ARCHIVED'
        const state = statusTagOf(status, status === 'PUBLISHED')
        row.lifecycleStatus = status
        row.tag = state.tag
        row.tagType = state.type
        row.tagLabel = statusLabelOf(status, status === 'PUBLISHED')
        row.actions = statusActionsOf(status, status === 'APPROVED', status === 'PUBLISHED')
        row.updatedAt = new Date().toISOString()
        ElMessage.success(`${capabilityActionLabels[action]}已完成`)
        dialogKey.value = ''
        reason.value = ''
        return
      }
      const state = loadDemoCapabilityState(bundledCapabilities)
      transitionDemoCapability(state, row.key, action, '平台审核管理员', feedback)
      ElMessage.success(`${capabilityActionLabels[action]}已完成`)
      dialogKey.value = ''
      reason.value = ''
      await load()
      return
    }
    if (!row.version) return
    await portalApi.capabilityAction(row.version, action, feedback)
    ElMessage.success('操作已提交')
    dialogKey.value = ''
    reason.value = ''
    await load()
  } catch (error) {
    failure.value = capabilityOperationError(error)
  } finally {
    busyKey.value = ''
  }
}

async function download(row: WorkbenchRow) {
  if (!row.asset || !row.version) return
  busyKey.value = row.key
  failure.value = ''
  try {
    const response = await portalApi.downloadCapabilityVersion(row.asset.id, row.version.versionId)
    const header = String(response.headers['content-disposition'] || '')
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)?.[1]
    if (!encoded) throw new Error('下载响应缺少文件名')
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = decodeURIComponent(encoded)
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    failure.value = capabilityOperationError(error)
  } finally {
    busyKey.value = ''
  }
}

function askAction(action: CapabilityAction, row: WorkbenchRow) { ask(row) }
function actionLabel(action: CapabilityAction) { return capabilityActionLabels[action] }
function kindLabel(value: DeliverableKind) { return value === 'skill' ? 'Skill' : value === 'tool' ? '工具' : '知识库' }
/** 表格里按本地时区显示到分钟，避免把 ISO 的 UTC 时间直接当成本地时间。 */
function shortTime(value: string) {
  if (!value || value === '—') return value
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

onMounted(load)
</script>

<template>
  <section class="approvals">
    <header class="page-title">
      <div>
        <span>ADMIN · APPROVALS</span>
        <h1>审批工作台</h1>
        <p>按交付物分类和审批状态查看员工已提交的版本，并在对应分组内完成审核、发布、下架和归档。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <nav class="kind-switch" aria-label="交付物分类">
      <button v-for="item in kinds" :key="item.key" :class="{ active: kind === item.key }" @click="switchKind(item.key)">{{ item.label }}</button>
      <span class="kind-detail">{{ activeKind.detail }}</span>
    </nav>

    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon class="failure" />

    <p v-if="kind === 'knowledge' && knowledgeAssets.length > knowledgeDemoLimit" class="demo-note">
      演示显示前 {{ knowledgeDemoLimit }} 项，交付基线库共 {{ knowledgeAssets.length }} 项材料。
    </p>

    <div class="stat-row">
      <div class="stat"><b>{{ versionTotal }}</b><span>全部版本</span></div>
      <div class="stat"><b>{{ reviewTotal }}</b><span>待审核</span></div>
      <div class="stat"><b>{{ approvedTotal }}</b><span>待发布</span></div>
      <div class="stat"><b>{{ publishedTotal }}</b><span>已发布</span></div>
      <div class="stat"><b>{{ rejectedTotal }}</b><span>已退回</span></div>
    </div>

    <section v-for="group in groups" :key="group.key" class="group" :class="{ empty: !group.items.length }">
      <header>
        <h3>{{ group.name }}<em>{{ group.items.length }}</em></h3>
        <p>{{ group.detail }}</p>
      </header>
      <el-table v-if="group.items.length" :data="group.items" size="large" :row-key="(row: WorkbenchRow) => row.key">
        <el-table-column label="交付物" min-width="240">
          <template #default="{ row }">
            <div class="capability"><b>{{ row.title }}</b><small>{{ row.subtitle }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="86">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ kindLabel(row.kind) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="132">
          <template #default="{ row }"><el-tag :type="row.tagType" effect="plain">{{ row.tagLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="提交人" width="140">
          <template #default="{ row }">{{ row.submitterId }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="140">
          <template #default="{ row }">{{ shortTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="版本更新时间" width="140">
          <template #default="{ row }">{{ shortTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="310">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-for="action in row.actions" :key="action" size="small" :type="action === 'approve' || action === 'publish' ? 'primary' : 'default'" :disabled="busyKey === row.key" @click="askAction(action, row)">{{ actionLabel(action) }}</el-button>
              <el-button v-if="row.packageAvailable && !row.demo" size="small" :icon="Download" :disabled="busyKey === row.key" @click="download(row)">下载包</el-button>
              <el-button size="small" plain @click="openHistory(row)">历史</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><span>{{ group.empty }}</span></template>
      </el-table>
      <p v-else class="empty-hint">{{ group.empty }}</p>
    </section>

    <el-dialog :model-value="Boolean(target)" :title="target ? `${target.title} · ${target.subtitle}` : '治理操作'" width="min(520px, 92vw)" @update:model-value="dialogKey = ''">
      <template v-if="target">
        <p class="dialog-status">当前状态：<el-tag :type="target.tagType" effect="plain" size="small">{{ target.tagLabel }}</el-tag></p>
        <p class="dialog-hint">请填写操作意见（不超过 1000 字），将记入审计记录。</p>
        <el-input v-model="reason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="审核意见、退回原因或发布说明" />
        <p class="dialog-busy">{{ dialogBusy ? '正在执行，请稍候…' : '' }}</p>
      </template>
      <template #footer>
        <el-button :disabled="dialogBusy" @click="dialogKey = ''">取消</el-button>
        <el-button v-for="action in dialogActions" :key="action" :type="action === 'approve' || action === 'publish' ? 'primary' : 'default'" :loading="dialogBusy" :disabled="!reason.trim() || dialogBusy" @click="target && act(action, target)">{{ actionLabel(action) }}</el-button>
      </template>
    </el-dialog>

    <!-- 历史记录维护：当前为示例数据，见 src/data/historyDemo.ts -->
    <el-drawer :model-value="Boolean(historyRow)" size="min(680px, 94vw)" direction="rtl" @update:model-value="closeHistory">
      <template #header>
        <div class="history-head">
          <span class="history-eyebrow">VERSION &amp; AUDIT HISTORY</span>
          <h2>{{ historyRow?.title }}</h2>
          <p>{{ historySubtitle }}</p>
        </div>
      </template>

      <div v-if="historyEmpty" class="history-blank">
        <el-icon class="history-blank-icon"><Document /></el-icon>
        <b>尚无历史记录</b>
        <p>这条交付物还没有可追溯的版本更替，通常是它首次提交审核、还没有走完第一次治理流程。</p>
        <p class="history-blank-hint">这里会按版本列出提交、审核、发布和归档记录；能力已有发布版本时，新版本的审批不会影响员工正在使用的版本。</p>
      </div>

      <template v-else>
        <h3 class="history-title">版本历史<em>{{ historyRecord?.versions.length ?? 0 }} 个</em></h3>
        <div class="history-versions">
          <button v-for="item in historyRecord?.versions ?? []" :key="item.version" type="button"
            class="history-version" :class="{ active: item.version === historyVersion, current: item.current }"
            @click="historyVersion = item.version">
            <span><b>v{{ item.version }}</b><i v-if="item.current">● 当前公开</i></span>
            <span>
              <el-tag size="small" effect="plain" :type="historyVersionLabel(item).type">{{ historyVersionLabel(item).text }}</el-tag>
              <small>资产 {{ item.assetStatus }} · 版本 {{ item.status }}</small>
            </span>
            <span class="history-time">{{ historyRecord ? versionUpdatedAt(historyRecord, item.version) : '' }}</span>
          </button>
        </div>

        <h3 class="history-title higher">操作记录<em>v{{ historyVersion }} · {{ historyScoped.length }} 条</em></h3>
        <ul class="history-timeline">
          <li v-for="event in historyScoped" :key="event.at + event.action" class="history-event" :class="event.tone">
            <div class="history-event-head"><b>{{ event.label }}</b><time>{{ event.at }}</time><span>{{ event.actor }}</span></div>
            <p>{{ event.reason }}</p>
            <small>{{ event.action }} · {{ event.effect }}</small>
          </li>
        </ul>
        <p v-if="!historyScoped.length" class="empty-hint">该版本暂无操作记录。</p>
      </template>

      <div v-if="!historyRow?.demo" class="history-note">
        <p>版本历史对应 <code>capability_version</code>，当前公开版本由 <code>capability_asset.current_version_id</code> 指向；操作记录对应 <code>audit_log</code>，字段为 action / actor_id / created_at / after_data.reason。</p>
        <p>把本页接到真实数据时需要：</p>
        <el-alert type="info" :closable="false" show-icon
          title="audit_log.target_id 存的是版本 ID，没有资产维度的聚合键，因此现在只能逐版本查询；建议为审计记录补一个资产维度，或新增资产级历史接口。" />
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.approvals{max-width:1400px;margin:0 auto;padding:36px 24px}
.page-title{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;margin-bottom:20px}
.page-title span{color:#ad2d2d;font-weight:800;letter-spacing:.1em}
.page-title h1{margin:8px 0}
.page-title p{color:#627086;margin:0}
.failure{margin-bottom:18px}
.kind-switch{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:22px;padding-bottom:16px;border-bottom:1px solid #e2e7ee}
.kind-switch button{min-width:104px;padding:11px 22px;font-size:16px;color:#3f4b60;background:#fff;border:1px solid #dce2ea;cursor:pointer}
.kind-switch button:hover{border-color:#ad2d2d;color:#ad2d2d}
.kind-switch button.active{color:#fff;background:#ad2d2d;border-color:#ad2d2d;font-weight:700}
.kind-detail{color:#627086;font-size:14px}
.demo-note{color:#627086;font-size:14px;margin:0 0 18px}
.stat-row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px;margin-bottom:26px}
.stat{padding:18px 20px;background:#fff;border:1px solid #dce2ea;border-left:4px solid #ad2d2d}
.stat b{display:block;font-size:28px;line-height:1.1}
.stat span{color:#627086;font-size:14px}
.group{margin-bottom:26px;padding:20px;background:#fff;border:1px solid #dce2ea}
.group header{margin-bottom:14px}
.group h3{margin:0 0 4px;font-size:19px;display:flex;align-items:center;gap:10px}
.group h3 em{font-style:normal;font-size:13px;font-weight:700;color:#ad2d2d;background:#fbeaea;border-radius:10px;padding:2px 10px}
.group header p{margin:0;color:#627086;font-size:14px}
.group.empty{background:#fafbfc}
.empty-hint{color:#8a95a6;margin:0;padding:8px 0}
.capability{display:flex;flex-direction:column}
.capability small{color:#627086}
.row-actions{display:flex;gap:8px;flex-wrap:wrap}
.row-actions .el-button+.el-button{margin-left:0}
.dialog-status{margin:0 0 10px}
.dialog-hint{color:#627086;font-size:14px;margin:0 0 10px}
.dialog-busy{color:#8a95a6;font-size:13px;margin:8px 0 0;min-height:18px}
/* 历史记录抽屉（示例数据） */
.history-head span{color:#ad2d2d;font-weight:800;letter-spacing:.1em;font-size:12px}
.history-head h2{margin:6px 0 4px;font-size:20px}
.history-head p{margin:0;color:#627086;font-size:13px}
.history-demo{margin-bottom:20px}
/* 首次上架、没有历史可看时的空态 */
.history-blank{padding:34px 20px;text-align:center;border:1px dashed #d5dbe4;background:#fafbfc}
.history-blank-icon{font-size:30px;color:#b9c2d0}
.history-blank b{display:block;margin:10px 0 6px;font-size:15px}
.history-blank p{margin:0 auto;max-width:420px;color:#627086;font-size:13px}
.history-blank .history-blank-hint{margin-top:8px;color:#8a95a6;font-size:12.5px}
.history-title{display:flex;align-items:baseline;gap:10px;margin:0 0 12px;font-size:15px}
.history-title.higher{margin-top:28px}
.history-title em{font-style:normal;color:#627086;font-size:12.5px;font-weight:400}
.history-versions{display:flex;flex-direction:column;gap:8px}
.history-version{display:grid;grid-template-columns:140px 1fr auto;gap:12px;align-items:center;padding:12px 14px;background:#fff;border:1px solid #dce2ea;border-left:3px solid transparent;cursor:pointer;text-align:left}
.history-version:hover{border-color:#cfd6e2;background:#fcfdfe}
.history-version.active{border-color:#ad2d2d;border-left-color:#ad2d2d;background:#fbeaea}
.history-version.current{border-left-color:#15803d}
.history-version b{font-size:14.5px}
.history-version i{font-style:normal;color:#ad2d2d;font-size:11.5px;font-weight:700}
.history-version small{display:block;color:#627086;font-size:12px;margin-top:3px}
.history-version .history-time{color:#627086;font-size:12.5px}
.history-timeline{list-style:none;margin:0;padding:0 0 0 22px;position:relative}
.history-timeline:before{content:"";position:absolute;left:5px;top:6px;bottom:6px;width:2px;background:#e2e7ef}
.history-event{position:relative;padding-bottom:18px}
.history-event:last-child{padding-bottom:0}
.history-event:before{content:"";position:absolute;left:-21px;top:6px;width:10px;height:10px;border-radius:50%;background:#fff;border:2px solid #b9c2d0}
.history-event.ok:before{border-color:#15803d;background:#15803d}
.history-event.warn:before{border-color:#b45309;background:#b45309}
.history-event.brand:before{border-color:#ad2d2d;background:#ad2d2d}
.history-event.retire:before{border-color:#64748b;background:#64748b}
.history-event-head{display:flex;align-items:baseline;gap:10px;flex-wrap:wrap}
.history-event-head b{font-size:14.5px}
.history-event-head time,.history-event-head span{color:#627086;font-size:12.5px}
.history-event p{margin:4px 0 0;padding:7px 11px;color:#3f4b60;font-size:13.5px;background:#f7f9fc;border-left:2px solid #e2e7ef}
.history-event small{display:block;margin-top:5px;color:#8a95a6;font-size:11.5px}
.history-note{margin-top:24px;padding:14px 16px;background:#fafbfc;border:1px dashed #d5dbe4}
.history-note p{margin:0 0 8px;color:#536077;font-size:13px}
.history-note p:last-child{margin-bottom:0}
@media(max-width:900px){.stat-row{grid-template-columns:repeat(2,minmax(0,1fr))}.page-title{flex-direction:column;align-items:flex-start}}
</style>
