import { hasRegisteredToolExecutor, hasRegisteredServerJobHandler } from './tools/runtime/registry.ts'
import capabilityManifestSchema from './generated/capability-manifest.schema.json' with { type: 'json' }
import { validateJsonSchema } from './jsonSchema.ts'

export type CapabilityKindV2 = 'skill' | 'tool'
export type CapabilityStatusV2 = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'
export type DeliveryModeV2 = 'package' | 'online' | 'hybrid'
export type CapabilitySchemaVersion = '2.0' | '2.1'
export type CapabilityRendererV21 = 'document-transform' | 'server-job' | 'external-link'
export type CapabilityExecutionModeV21 = 'browser-local' | 'server-job' | 'external'
export type CapabilityToolInputKindV21 = 'markdown' | 'file'
export type CapabilityToolOutputKindV21 = 'file' | 'markdown'

export interface CapabilityItemV2 {
  name: string
  detail?: string
  formats?: string[]
}

export interface CapabilityToolOperationV21 {
  id: string
  label: string
  from: string
  to: string
  hint: string
  executor: string
  input: {
    kind: CapabilityToolInputKindV21
    accept: string[]
    heading: string
    description: string
  }
  output: {
    kind: CapabilityToolOutputKindV21
    format: string
    actionLabel: string
  }
  notices?: string[]
}

export interface CapabilityRuntimeV21 {
  renderer: CapabilityRendererV21
  executionMode: CapabilityExecutionModeV21
  route: string
  fileLimitMB: number
  securityStatement: string
  supportNotes: string[]
  operations: CapabilityToolOperationV21[]
  handler?: string
  sampleMarkdown?: string
  templates?: string[]
}

export interface CapabilityManifestV2 {
  schemaVersion: CapabilitySchemaVersion
  kind: CapabilityKindV2
  identity: {
    slug: string
    name: string
    shortName?: string
    tagline: string
    description: string
    version: string
    maintainer: string
    updatedAt: string
    accent?: string
  }
  classification: {
    stageIds: number[]
    tags: string[]
    audiences: string[]
  }
  usage: {
    triggers?: string[]
    scenarios: string[]
    inputs: CapabilityItemV2[]
    outputs: CapabilityItemV2[]
    workflow: Array<{ title: string; detail: string }>
    quickStart: string[]
  }
  quality: {
    dimensions: Array<{ name: string; criteria: string; evidence: string }>
    humanReview: string[]
    boundaries: string[]
    warnings?: Array<{
      text: string
      source: { file: string; line?: number; quote?: string }
      level?: 'NOTICE' | 'CAUTION' | 'BLOCKER'
    }>
  }
  delivery: {
    mode: DeliveryModeV2
    package?: {
      environment: string
      installGuide: string
      packageItems: Array<{ name: string; detail?: string }>
    }
    online?: {
      enabled: boolean
      route?: string
      acceptedExtensions: string[]
      maxFileSizeMB: number
      processingLocation: string
      externalProviders: string[]
      retentionPolicy: string
      dataNotice: string
    }
  }
  governance: {
    status: CapabilityStatusV2
    reviewer?: string
    reviewedAt?: string
    changeNote: string
  }
  references: {
    manual?: string
    faq?: string
    changelog?: string
  }
  provenance?: Array<{
    path: string
    status: 'EXTRACTED' | 'USER_CONFIRMED' | 'USER_EDITED'
    source?: { file: string; line?: number; quote?: string }
    note?: string
  }>
  /** 2.1 起工具运行定义与业务资料保存在同一份 capability.json 中。 */
  runtime?: CapabilityRuntimeV21
}

export function validateCapabilityManifest(value: unknown): string[] {
  const errors = validateJsonSchema(value, capabilityManifestSchema)
  if (!value || typeof value !== 'object' || Array.isArray(value)) return errors
  const item = value as Partial<CapabilityManifestV2>
  if (item.runtime) errors.push(...validateCapabilityRuntime(item.runtime))
  if (item.runtime && item.identity?.slug && item.runtime.route !== `/tools/${item.identity.slug}`) errors.push('runtime.route 必须匹配能力标识')
  if (item.runtime && item.delivery?.online && item.runtime.fileLimitMB !== item.delivery.online.maxFileSizeMB) errors.push('runtime 与 online 的文件上限必须一致')
  return errors
}

export function validateCapabilityRuntime(runtime: CapabilityRuntimeV21): string[] {
  const errors: string[] = []
  if (!runtime || typeof runtime !== 'object' || Array.isArray(runtime)) return ['runtime 必须为对象']
  if (!['document-transform', 'server-job', 'external-link'].includes(runtime.renderer)) errors.push('runtime.renderer 未注册')
  if (!nonemptyText(runtime.route)) errors.push('runtime.route 不能为空')
  if (!Number.isFinite(runtime.fileLimitMB) || runtime.fileLimitMB < 0 || runtime.fileLimitMB > 500) errors.push('runtime.fileLimitMB 必须在 0 到 500 之间')
  if (!nonemptyText(runtime.securityStatement)) errors.push('runtime.securityStatement 不能为空')
  if (!['browser-local', 'server-job', 'external'].includes(runtime.executionMode)) errors.push('runtime.executionMode 无效')
  if (!Array.isArray(runtime.operations) || !textList(runtime.supportNotes)) errors.push('runtime.operations 必须为数组，supportNotes 必须为文字列表')
  if (runtime.templates !== undefined && !textList(runtime.templates)) errors.push('runtime.templates 必须为文字列表')
  if (runtime.sampleMarkdown !== undefined && typeof runtime.sampleMarkdown !== 'string') errors.push('runtime.sampleMarkdown 必须为文字')
  if (runtime.renderer === 'document-transform') {
    if (runtime.executionMode !== 'browser-local') errors.push('文档转换器必须使用 browser-local')
    if (!runtime.operations?.length) errors.push('document-transform 至少需要一个 operation')
    const operationIds = new Set<string>()
    for (const operation of Array.isArray(runtime.operations) ? runtime.operations : []) {
      if (!operation || typeof operation !== 'object') { errors.push('operation 必须为对象'); continue }
      if (!['id', 'label', 'from', 'to', 'hint', 'executor'].every(key => nonemptyText(operation[key as keyof CapabilityToolOperationV21]))) errors.push('operation 的标识、名称、转换方向、说明和执行器不能为空')
      if (!hasRegisteredToolExecutor(operation.executor)) errors.push(`平台未注册执行器：${operation.executor || '未填写'}`)
      if (operationIds.has(operation.id)) errors.push(`operation.id 重复：${operation.id}`)
      operationIds.add(operation.id)
      if (!nonemptyStrings(operation.input?.accept) || !['markdown', 'file'].includes(operation.input?.kind) || !nonemptyText(operation.input?.heading) || !nonemptyText(operation.input?.description)) errors.push(`operation ${operation.id || '未命名'} 未完整配置输入`)
      if (!['file', 'markdown'].includes(operation.output?.kind) || !nonemptyText(operation.output?.format) || !nonemptyText(operation.output?.actionLabel)) errors.push(`operation ${operation.id || '未命名'} 未配置输出`)
      if (operation.notices !== undefined && !textList(operation.notices)) errors.push('operation.notices 必须为文字列表')
    }
  }
  if (runtime.renderer === 'server-job') {
    if (runtime.executionMode !== 'server-job') errors.push('服务端任务必须使用 server-job')
    if (!hasRegisteredServerJobHandler(runtime.handler)) errors.push('server-job 必须声明已注册的 runtime.handler')
  }
  return errors
}

function nonemptyText(value: unknown): value is string { return typeof value === 'string' && Boolean(value.trim()) }
function textList(value: unknown): value is string[] { return Array.isArray(value) && value.every(nonemptyText) }
function nonemptyStrings(value: unknown): value is string[] { return Array.isArray(value) && value.length > 0 && value.every(nonemptyText) }
export function capabilityTemplate(kind: CapabilityKindV2): CapabilityManifestV2 {
  const today = new Date().toISOString().slice(0, 10)
  const common: CapabilityManifestV2 = {
    schemaVersion: '2.1', kind,
    identity: { slug: '', name: '', tagline: '', description: '', version: '1.0.0', maintainer: '', updatedAt: today, accent: '#ad2d2d' },
    classification: { stageIds: [], tags: [], audiences: [] },
    usage: {
      scenarios: [], inputs: [], outputs: [], workflow: [], quickStart: [],
    },
    quality: {
      dimensions: [], humanReview: [], boundaries: [], warnings: [],
    },
    delivery: {
      mode: kind === 'skill' ? 'package' : 'online',
      package: kind === 'skill' ? { environment: '', installGuide: '', packageItems: [] } : undefined,
      online: kind === 'tool' ? { enabled: false, acceptedExtensions: [], maxFileSizeMB: 0, processingLocation: '', externalProviders: [], retentionPolicy: '', dataNotice: '' } : undefined,
    },
    governance: { status: 'DRAFT', changeNote: '首次录入' },
    references: {},
    provenance: [],
    runtime: undefined,
  }
  return common
}
