import type { CapabilityAction, CapabilityAudit, ManagedCapability } from './api/portal'
import type { CapabilityManifestV2 } from './capabilitySchema'

export const capabilityActionLabels: Record<CapabilityAction, string> = {
  submit: '提交审核', approve: '审核通过', reject: '退回修改', publish: '发布版本', unpublish: '下架能力', archive: '归档版本',
}

export function capabilityStatusLabel(record: ManagedCapability): string {
  if (record.currentlyPublished) return '当前发布'
  if (record.status === 'PUBLISHED') return record.currentVersionId === record.versionId && record.assetStatus === 'ARCHIVED' ? '已下架' : '历史发布'
  if (record.status === 'IN_REVIEW') return record.reviewApproved ? '审核通过，待发布' : '待审核'
  return record.status === 'ARCHIVED' ? '已归档' : '草稿'
}

export function isCapabilityContributor(record: ManagedCapability, audits: CapabilityAudit[], actorId?: string): boolean {
  return Boolean(actorId && (record.createdBy === actorId || audits.some(log => log.actorId === actorId && ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT'].includes(log.action))))
}

export function availableCapabilityActions(record: ManagedCapability, contributor: boolean): CapabilityAction[] {
  if (!record.versionId || !record.updatedAt) return []
  if (record.status === 'DRAFT') return ['submit', 'archive']
  if (record.status === 'IN_REVIEW') {
    if (record.reviewApproved) return contributor ? ['publish'] : ['publish', 'reject']
    return contributor ? [] : ['approve', 'reject']
  }
  if (record.status === 'PUBLISHED') return record.currentlyPublished ? ['unpublish'] : ['archive']
  return []
}

export function draftCapability(source: CapabilityManifestV2, newVersion = false): CapabilityManifestV2 {
  const result: CapabilityManifestV2 = JSON.parse(JSON.stringify(source))
  result.governance = { status: 'DRAFT', changeNote: newVersion ? '' : result.governance.changeNote }
  if (newVersion) result.identity.version = ''
  return result
}

export function capabilityOperationError(error: unknown): string {
  const response = (error as { response?: { data?: { message?: unknown } } })?.response
  return typeof response?.data?.message === 'string' ? response.data.message : '操作未能确认成功，请刷新版本状态并检查服务连接。'
}
