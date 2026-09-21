import type { CapabilityManifestV2 } from './capabilitySchema'
import type { CapabilityAction, ManagedCapability } from './api/portal'

export type DemoLifecycleStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED' | 'DELISTED' | 'REJECTED'

export interface DemoManagedCapability extends ManagedCapability {
  demoLifecycle?: DemoLifecycleStatus
}

export interface DemoCapabilityState {
  schemaVersion: 3
  signature: string
  records: DemoManagedCapability[]
}

const storageKey = 'sp-ai-portal-demo-capability-governance-v3'
let memoryState: DemoCapabilityState | undefined

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function signatureOf(manifests: CapabilityManifestV2[]): string {
  return manifests
    .map(item => `${item.identity.slug}@${item.identity.version}`)
    .sort()
    .join('|')
}

function nextPatch(version: string): string {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version)
  if (!match) return `${version}.1`
  return `${match[1]}.${match[2]}.${Number(match[3]) + 1}`
}

function governanceManifest(source: CapabilityManifestV2, status: ManagedCapability['status'], changeNote: string): CapabilityManifestV2 {
  const result = clone(source)
  result.governance = {
    ...result.governance,
    status,
    changeNote,
    reviewer: status === 'IN_REVIEW' || status === 'PUBLISHED' ? '平台审核管理员' : undefined,
    reviewedAt: status === 'PUBLISHED' ? '2026-09-14T10:30:00+08:00' : undefined,
  }
  return result
}

function baseRecord(source: CapabilityManifestV2, index: number): DemoManagedCapability {
  const id = `demo-${source.identity.slug}`
  const versionId = `${id}-${source.identity.version}`
  const submitters = ['演示员工', '演示工具组', '演示平台组']
  return {
    id,
    versionId,
    assetStatus: 'PUBLISHED',
    currentVersionId: versionId,
    currentlyPublished: true,
    reviewApproved: true,
    pendingReviews: 0,
    createdBy: index % 2 ? '能力维护组' : '平台能力组',
    submitter: submitters[index % submitters.length],
    submittedAt: `2026-09-${String(5 + index).padStart(2, '0')}T10:00:00+08:00`,
    slug: source.identity.slug,
    kind: source.kind,
    name: source.identity.name,
    version: source.identity.version,
    status: 'PUBLISHED',
    packageFileId: `demo-package-${source.identity.slug}`,
    manifest: governanceManifest(source, 'PUBLISHED', '当前稳定发布版本'),
    createdAt: `2026-09-${String(4 + index).padStart(2, '0')}T09:00:00+08:00`,
    updatedAt: `2026-09-${String(6 + index).padStart(2, '0')}T12:10:00+08:00`,
    demoLifecycle: 'PUBLISHED',
  }
}

function followUpRecord(source: CapabilityManifestV2, published: DemoManagedCapability, index: number): DemoManagedCapability | undefined {
  const mode = index % 4
  const skillDemoStatus: Partial<Record<string, DemoLifecycleStatus | null>> = {
    'platform-skill-adapter': null,
  }
  const configured = source.kind === 'skill' ? skillDemoStatus[source.identity.slug] : undefined
  if (configured === null || (configured === undefined && mode === 3)) return undefined
  const version = nextPatch(source.identity.version)
  // 公开演示只保留通用流程状态，不携带真实业务案例。
  const lifecycle: DemoLifecycleStatus = configured ?? (mode === 2 ? 'ARCHIVED' : mode === 0 ? 'IN_REVIEW' : 'APPROVED')
  const status: ManagedCapability['status'] = lifecycle === 'ARCHIVED' ? 'ARCHIVED' : 'IN_REVIEW'
  const item = clone(source)
  item.identity.version = version
  const updatedAt = `2026-09-${String(12 + (index % 4)).padStart(2, '0')}T15:30:00+08:00`
  return {
    ...published,
    versionId: `${published.id}-${version}`,
    currentlyPublished: false,
    reviewApproved: lifecycle === 'APPROVED',
    pendingReviews: lifecycle === 'IN_REVIEW' ? 1 : 0,
    submitter: index % 2 ? '演示工具组' : '演示员工',
    submittedAt: updatedAt,
    version,
    status,
    manifest: governanceManifest(item, status, lifecycle === 'ARCHIVED' ? '历史版本归档' : '功能与资料更新'),
    createdAt: updatedAt,
    updatedAt,
    demoLifecycle: lifecycle,
  }
}

export function createDemoCapabilityState(manifests: CapabilityManifestV2[]): DemoCapabilityState {
  const records: DemoManagedCapability[] = []
  manifests.forEach((source, index) => {
    const published = baseRecord(source, index)
    records.push(published)
    const followUp = followUpRecord(source, published, index)
    if (followUp) records.push(followUp)
  })
  return { schemaVersion: 3, signature: signatureOf(manifests), records }
}

function localStore(): Storage | undefined {
  return typeof window !== 'undefined' && window.localStorage ? window.localStorage : undefined
}

export function loadDemoCapabilityState(manifests: CapabilityManifestV2[]): DemoCapabilityState {
  const signature = signatureOf(manifests)
  const store = localStore()
  try {
    const raw = store?.getItem(storageKey)
    const saved = raw ? JSON.parse(raw) as DemoCapabilityState : memoryState
    if (saved?.schemaVersion === 3 && saved.signature === signature && Array.isArray(saved.records)) {
      memoryState = saved
      return clone(saved)
    }
  } catch {
    // 本地演示状态损坏时回到随代码发布的基线数据。
  }
  const initial = createDemoCapabilityState(manifests)
  saveDemoCapabilityState(initial)
  return clone(initial)
}

export function saveDemoCapabilityState(state: DemoCapabilityState): void {
  memoryState = clone(state)
  try { localStore()?.setItem(storageKey, JSON.stringify(state)) } catch { /* 浏览器禁用存储时仍使用内存状态 */ }
}

export function demoCapabilityAssets(state: DemoCapabilityState): DemoManagedCapability[] {
  const groups = new Map<string, DemoManagedCapability[]>()
  state.records.forEach(record => groups.set(record.slug, [...(groups.get(record.slug) || []), record]))
  return [...groups.values()].map(versions => {
    const selected = versions.find(item => item.currentlyPublished)
      || versions.find(item => item.demoLifecycle === 'DELISTED')
      || [...versions].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0]
    const result = clone(selected)
    result.pendingReviews = versions.filter(item => item.status === 'IN_REVIEW' && !item.reviewApproved).length
    return result
  })
}

export function saveDemoCapabilityDraft(state: DemoCapabilityState, source: CapabilityManifestV2, actor: string): DemoManagedCapability {
  const manifest = governanceManifest(source, 'DRAFT', source.governance.changeNote || '能力资料更新')
  const versions = state.records.filter(item => item.slug === source.identity.slug)
  const current = versions.find(item => item.currentlyPublished)
  const sameVersion = versions.find(item => item.version === source.identity.version)
  if (sameVersion && sameVersion.status !== 'DRAFT') throw new Error(`版本 ${source.identity.version} 已存在，请填写新的版本号`)
  const id = current?.id || sameVersion?.id || `demo-${source.identity.slug}`
  const now = new Date().toISOString()
  const record: DemoManagedCapability = {
    id,
    versionId: `${id}-${source.identity.version}`,
    assetStatus: current ? 'PUBLISHED' : 'DRAFT',
    currentVersionId: current?.versionId || `${id}-${source.identity.version}`,
    currentlyPublished: false,
    reviewApproved: false,
    pendingReviews: 0,
    createdBy: actor,
    submitter: actor,
    slug: source.identity.slug,
    kind: source.kind,
    name: source.identity.name,
    version: source.identity.version,
    status: 'DRAFT',
    packageFileId: `demo-package-${source.identity.slug}-${source.identity.version}`,
    manifest,
    createdAt: sameVersion?.createdAt || now,
    updatedAt: now,
    demoLifecycle: 'DRAFT',
  }
  state.records = [...state.records.filter(item => item.versionId !== record.versionId), record]
  saveDemoCapabilityState(state)
  return clone(record)
}

export function transitionDemoCapability(state: DemoCapabilityState, versionId: string, action: CapabilityAction, actor: string, reason: string): DemoManagedCapability {
  const record = state.records.find(item => item.versionId === versionId)
  if (!record) throw new Error('未找到要操作的版本')
  const now = new Date().toISOString()
  if (action === 'submit') {
    if (record.status !== 'DRAFT') throw new Error('只有草稿可以提交审核')
    record.status = 'IN_REVIEW'; record.reviewApproved = false; record.demoLifecycle = 'IN_REVIEW'; record.submittedAt = now; record.submitter = actor
  } else if (action === 'approve') {
    if (record.status !== 'IN_REVIEW' || record.reviewApproved) throw new Error('当前版本不在待审核状态')
    record.reviewApproved = true; record.demoLifecycle = 'APPROVED'
  } else if (action === 'reject') {
    if (record.status !== 'IN_REVIEW') throw new Error('当前版本不在审核流程中')
    record.status = 'DRAFT'; record.reviewApproved = false; record.demoLifecycle = 'REJECTED'
  } else if (action === 'publish') {
    if (record.status !== 'IN_REVIEW' || !record.reviewApproved) throw new Error('版本需要先审核通过才能发布')
    state.records.filter(item => item.slug === record.slug && item.currentlyPublished).forEach(item => {
      item.currentlyPublished = false; item.status = 'ARCHIVED'; item.demoLifecycle = 'ARCHIVED'; item.updatedAt = now
    })
    record.status = 'PUBLISHED'; record.currentlyPublished = true; record.reviewApproved = true; record.demoLifecycle = 'PUBLISHED'
    state.records.filter(item => item.slug === record.slug).forEach(item => { item.assetStatus = 'PUBLISHED'; item.currentVersionId = record.versionId })
  } else if (action === 'unpublish') {
    if (!record.currentlyPublished) throw new Error('只有当前发布版本可以下架')
    record.currentlyPublished = false; record.assetStatus = 'ARCHIVED'; record.demoLifecycle = 'DELISTED'
    state.records.filter(item => item.slug === record.slug).forEach(item => { item.assetStatus = 'ARCHIVED'; item.currentVersionId = record.versionId })
  } else if (action === 'archive') {
    record.status = 'ARCHIVED'; record.currentlyPublished = false; record.demoLifecycle = 'ARCHIVED'
  }
  record.manifest = governanceManifest(record.manifest, record.status, reason)
  if (record.manifest.governance.reviewer !== undefined) record.manifest.governance.reviewer = actor
  record.updatedAt = now
  saveDemoCapabilityState(state)
  return clone(record)
}
