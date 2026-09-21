import type { CapabilitySelection } from '../../capabilityAccess'
import { hasRegisteredToolExecutor, hasRegisteredServerJobHandler } from './registry.ts'

export function toolUnavailableReason(selected?: CapabilitySelection): string {
  if (!selected) return '工具资料尚未读取'
  const { manifest, source } = selected
  if (manifest.kind !== 'tool') return '该能力以 Skill 包交付，请查看使用说明'
  if (manifest.governance.status !== 'PUBLISHED') return '该工具尚未发布或已停止发布'
  if (!manifest.delivery.online?.enabled) return '该工具尚未开放在线处理'
  const runtimeProblem = runtimeUnavailableReason(selected)
  if (runtimeProblem) return runtimeProblem
  if (manifest.runtime?.renderer === 'server-job' && source !== 'backend') return '当前是本地资料预览，服务端工具需读取后台已发布资料后才能提交文件'
  return ''
}

/** Entering the workspace is separate from authorizing server processing. */
export function toolExperienceAccess(selected?: CapabilitySelection): { reason: string; serverPreview: boolean } {
  const blocked = (reason: string) => ({ reason, serverPreview: true })
  if (!selected) return blocked('工具资料尚未读取')
  const { manifest, source } = selected
  if (manifest.kind !== 'tool') return blocked('该能力以 Skill 包交付，请查看使用说明')
  if (manifest.governance.status === 'ARCHIVED' || (source === 'backend' && manifest.governance.status !== 'PUBLISHED')) return blocked('该工具尚未发布或已停止发布')
  const problem = runtimeUnavailableReason(selected)
  if (problem) return blocked(problem)
  if (manifest.runtime?.renderer === 'server-job') return { reason: '', serverPreview: source === 'bundled' || !manifest.delivery.online?.enabled }
  // Explicit local development catalog can exercise real browser converters without claiming publication.
  if (source === 'bundled') return { reason: '', serverPreview: false }
  return { reason: toolUnavailableReason(selected), serverPreview: false }
}

function runtimeUnavailableReason({ manifest }: CapabilitySelection): string {
  const runtime = manifest.runtime
  if (!runtime) return '该工具尚未配置运行方式'
  if (runtime.route !== `/tools/${manifest.identity.slug}`) return '运行入口与能力标识不一致，请由维护人检查'
  if (runtime.renderer === 'document-transform') {
    if (runtime.executionMode !== 'browser-local') return '文档转换器必须使用浏览器本地处理'
    if (!runtime.operations.length || runtime.operations.some(operation => !hasRegisteredToolExecutor(operation.executor))) return '该工具引用了尚未接入的转换方式'
    return ''
  }
  if (runtime.renderer === 'server-job') {
    if (runtime.executionMode !== 'server-job' || !hasRegisteredServerJobHandler(runtime.handler)) return '该工具的服务端处理器尚未接入'
    return ''
  }
  return '该工具的运行方式尚未接入'
}
