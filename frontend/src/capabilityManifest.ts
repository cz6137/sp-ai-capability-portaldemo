import type { CapabilityManifestV2, CapabilityRuntimeV21 } from './capabilitySchema'

export type CapabilityKind = 'skill' | 'tool'
export type TrialMode = 'disabled' | 'sample-only' | 'sandbox-upload' | 'browser-local'

export interface CapabilityIO {
  name: string
  detail: string
  formats?: string
}

export interface CapabilityManifestBase {
  schemaVersion: string
  kind: CapabilityKind
  id: string
  slug: string
  name: string
  tagline: string
  description: string
  accent: string
  icon?: string
  version: string
  updatedAt: string
  maintainer: string
  maturity: string
  tags: string[]
  metrics: Array<{ value: string; label: string }>
  inputs: CapabilityIO[]
  outputs: CapabilityIO[]
  qualityChecks: string[]
  boundaries: string[]
  deployment: {
    available: boolean
    modes: string[]
    environment: string
    guide: string
  }
}

export interface ToolTrialPolicy {
  enabled: boolean
  mode: TrialMode
  prototype: boolean
  actualFileTransfer: boolean
  acceptedExtensions: string[]
  maxFileSizeMB: number
  retentionHours: number
  processingLocation: string
  externalProviders: string[]
  requireHumanReview: boolean
  resultWatermark: string
  steps: string[]
  templates: string[]
  sample: {
    fileName: string
    subject: string
    attendees: string
    background: string
    template: string
  }
  qualityDimensions: Array<{ name: string; status: string; detail: string }>
}

export interface ToolManifest extends CapabilityManifestBase {
  kind: 'tool'
  trial: ToolTrialPolicy
}

export type ToolRenderer = 'document-transform' | 'custom'
export type ToolExecutionMode = 'browser-local' | 'server-job'
export type ToolInputKind = 'markdown' | 'file'
export type ToolOutputKind = 'file' | 'markdown'

export interface ToolOperationDefinition {
  id: string
  label: string
  from: string
  to: string
  hint: string
  executor: string
  input: {
    kind: ToolInputKind
    accept: string[]
    heading: string
    description: string
  }
  output: {
    kind: ToolOutputKind
    format: string
    actionLabel: string
  }
  notices?: string[]
}

export interface ToolRuntimeDefinition {
  renderer: ToolRenderer
  executionMode: ToolExecutionMode
  handler?: string
  route: string
  fileLimitMB: number
  securityStatement: string
  sampleMarkdown?: string
  supportNotes: string[]
  operations: ToolOperationDefinition[]
}

export interface ToolDefinition extends ToolManifest {
  runtime: ToolRuntimeDefinition
}

export function validateToolManifest(tool: ToolManifest): string[] {
  const errors: string[] = []
  if (tool.kind !== 'tool') errors.push('kind 必须为 tool')
  if (!tool.id || !tool.name || !tool.version) errors.push('缺少 id、name 或 version')
  if (tool.trial.mode === 'sample-only' && tool.trial.actualFileTransfer) errors.push('sample-only 不允许真实文件传输')
  if (tool.trial.mode === 'sandbox-upload' && tool.trial.maxFileSizeMB <= 0) errors.push('受控上传必须配置文件大小上限')
  if (tool.trial.mode === 'browser-local' && tool.trial.actualFileTransfer) errors.push('browser-local 不允许向服务端传输文件')
  if (tool.trial.retentionHours < 0) errors.push('留存时间不能为负数')
  if (!tool.trial.requireHumanReview) errors.push('工具结果必须配置人工复核要求')
  return errors
}

export function validateToolDefinition(tool: ToolDefinition): string[] {
  const errors = validateToolManifest(tool)
  if (!tool.runtime?.renderer || !tool.runtime.route) errors.push('缺少 runtime.renderer 或 runtime.route')
  if (tool.runtime?.fileLimitMB <= 0) errors.push('runtime.fileLimitMB 必须大于 0')
  if (tool.runtime?.renderer === 'document-transform') {
    if (!tool.runtime.operations.length) errors.push('document-transform 至少需要一个 operation')
    const operationIds = new Set<string>()
    for (const operation of tool.runtime.operations) {
      if (!operation.id || !operation.executor) errors.push('operation 缺少 id 或 executor')
      if (operationIds.has(operation.id)) errors.push(`operation.id 重复：${operation.id}`)
      operationIds.add(operation.id)
      if (!operation.input.accept.length) errors.push(`operation ${operation.id} 未配置输入格式`)
      if (!operation.output.format || !operation.output.actionLabel) errors.push(`operation ${operation.id} 未配置输出`)
    }
  }
  return errors
}

/**
 * 过渡期兼容器：旧 tool.json 只作为导入格式，进入平台后立即转换成 2.1。
 * 新页面和后端不应再把旧结构作为业务事实源。
 */
export function legacyToolToCapability(tool: ToolDefinition): CapabilityManifestV2 {
  const runtime: CapabilityRuntimeV21 = {
    renderer: tool.runtime.renderer === 'custom' ? 'server-job' : tool.runtime.renderer,
    executionMode: tool.runtime.executionMode,
    route: tool.runtime.route,
    fileLimitMB: tool.runtime.fileLimitMB,
    securityStatement: tool.runtime.securityStatement,
    supportNotes: tool.runtime.supportNotes || [],
    operations: tool.runtime.operations.map(operation => ({
      ...operation,
      input: { ...operation.input },
      output: { ...operation.output },
    })),
    handler: tool.runtime.renderer === 'custom' ? `${tool.id}-v1` : undefined,
    sampleMarkdown: tool.runtime.sampleMarkdown,
    templates: tool.trial.templates,
  }
  const browserLocal = tool.runtime.executionMode === 'browser-local'
  return {
    schemaVersion: '2.1',
    kind: 'tool',
    identity: {
      slug: tool.slug,
      name: tool.name,
      tagline: tool.tagline,
      description: tool.description,
      version: tool.version,
      maintainer: tool.maintainer,
      updatedAt: tool.updatedAt,
      accent: tool.accent,
    },
    classification: { stageIds: [], tags: tool.tags || [], audiences: [] },
    usage: {
      scenarios: [tool.description],
      inputs: tool.inputs.map(item => ({ name: item.name, detail: item.detail, formats: splitFormats(item.formats) })),
      outputs: tool.outputs.map(item => ({ name: item.name, detail: item.detail, formats: splitFormats(item.formats) })),
      workflow: (tool.trial.steps || []).map(step => ({ title: step, detail: `按“${step}”完成处理，并保留必要的人工确认。` })),
      quickStart: ['确认数据处理位置与使用边界', '准备输入材料并选择处理方式', '检查结果后再下载或进入正式流程'],
    },
    quality: {
      dimensions: (tool.qualityChecks.length ? tool.qualityChecks : ['内容']).map(name => ({ name, criteria: '结果应与输入材料和业务语境一致', evidence: '保留输入、输出和人工确认记录' })),
      humanReview: ['正式使用前由业务人员确认输出内容'],
      boundaries: tool.boundaries,
    },
    delivery: {
      mode: tool.deployment.available ? 'hybrid' : 'online',
      package: tool.deployment.available ? {
        environment: tool.deployment.environment,
        installGuide: tool.deployment.guide,
        packageItems: [{ name: '工具部署包', detail: '具体文件以实际上传并审核的交付包为准' }],
      } : undefined,
      online: {
        enabled: tool.trial.enabled,
        route: tool.runtime.route,
        acceptedExtensions: tool.trial.acceptedExtensions,
        maxFileSizeMB: tool.trial.maxFileSizeMB,
        processingLocation: tool.trial.processingLocation,
        externalProviders: tool.trial.externalProviders,
        retentionPolicy: browserLocal ? '文件和结果只在当前浏览器处理，关闭页面后不留存' : `${tool.trial.retentionHours} 小时`,
        dataNotice: tool.runtime.securityStatement,
      },
    },
    governance: { status: 'DRAFT', changeNote: `从 tool.json ${tool.version} 迁移` },
    references: {},
    runtime,
  }
}

/** 现有运行组件的临时适配器；运行组件完成 2.1 改造后删除。 */
export function capabilityToLegacyTool(capability: CapabilityManifestV2): ToolDefinition {
  if (capability.kind !== 'tool' || !capability.runtime) throw new Error('该能力没有可运行的工具定义')
  const online = capability.delivery.online
  const runtime = capability.runtime
  const browserLocal = runtime.executionMode === 'browser-local'
  return {
    schemaVersion: capability.schemaVersion,
    kind: 'tool',
    id: capability.identity.slug,
    slug: capability.identity.slug,
    name: capability.identity.name,
    tagline: capability.identity.tagline,
    description: capability.identity.description,
    accent: capability.identity.accent || '#ad2d2d',
    icon: runtime.renderer === 'server-job' ? 'headset' : 'document',
    version: capability.identity.version,
    updatedAt: capability.identity.updatedAt,
    maintainer: capability.identity.maintainer,
    maturity: ({ DRAFT: '草稿', IN_REVIEW: '待审核', PUBLISHED: '已发布', ARCHIVED: '已归档' } as const)[capability.governance.status],
    tags: capability.classification.tags,
    metrics: [],
    inputs: capability.usage.inputs.map(item => ({ ...item, detail: item.detail || '', formats: item.formats?.join(' / ') })),
    outputs: capability.usage.outputs.map(item => ({ ...item, detail: item.detail || '', formats: item.formats?.join(' / ') })),
    qualityChecks: capability.quality.dimensions.map(item => item.name),
    boundaries: capability.quality.boundaries,
    deployment: {
      available: Boolean(capability.delivery.package),
      modes: capability.delivery.package ? ['能力包 / 本地部署'] : ['平台在线使用'],
      environment: capability.delivery.package?.environment || online?.processingLocation || '平台在线环境',
      guide: capability.delivery.package?.installGuide || online?.dataNotice || '',
    },
    trial: {
      enabled: Boolean(online?.enabled),
      mode: browserLocal ? 'browser-local' : online?.enabled ? 'sandbox-upload' : 'disabled',
      prototype: false,
      actualFileTransfer: !browserLocal && Boolean(online?.enabled),
      acceptedExtensions: online?.acceptedExtensions || [],
      maxFileSizeMB: online?.maxFileSizeMB || runtime.fileLimitMB,
      retentionHours: 0,
      processingLocation: online?.processingLocation || (browserLocal ? '用户当前浏览器' : '平台后端'),
      externalProviders: online?.externalProviders || [],
      requireHumanReview: capability.quality.humanReview.length > 0,
      resultWatermark: '机器或程序生成结果，须人工确认后使用',
      steps: capability.usage.workflow.map(item => item.title),
      templates: runtime.templates || [],
      sample: { fileName: '', subject: '', attendees: '', background: '', template: '' },
      qualityDimensions: [],
    },
    runtime: {
      renderer: runtime.renderer === 'server-job' ? 'custom' : runtime.renderer === 'document-transform' ? 'document-transform' : 'custom',
      executionMode: runtime.executionMode === 'external' ? 'server-job' : runtime.executionMode,
      handler: runtime.handler,
      route: runtime.route,
      fileLimitMB: runtime.fileLimitMB,
      securityStatement: runtime.securityStatement,
      sampleMarkdown: runtime.sampleMarkdown,
      supportNotes: runtime.supportNotes,
      operations: runtime.operations.map(operation => ({
        ...operation,
        input: { ...operation.input },
        output: { ...operation.output },
      })),
    },
  }
}

function splitFormats(value?: string) {
  return value?.split('/').map(item => item.trim()).filter(Boolean)
}
