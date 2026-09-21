import type { ManagedCapability } from './api/portal'
import { validateCapabilityManifest, type CapabilityKindV2, type CapabilityManifestV2 } from './capabilitySchema.ts'

export type CapabilitySource = 'backend' | 'bundled'
export interface CapabilitySelection {
  manifest: CapabilityManifestV2
  source: CapabilitySource
  record?: ManagedCapability
}

interface CapabilityApi {
  capabilities(params: { kind: CapabilityKindV2 }): Promise<ManagedCapability[]>
  capability(slug: string): Promise<ManagedCapability>
}

export function resolveCapabilitySource(value: string | undefined, development: boolean): CapabilitySource {
  const mode = value || (development ? 'bundled' : 'backend')
  if (mode !== 'backend' && mode !== 'bundled') throw new Error('能力数据来源配置无效')
  return mode
}

/** 本地资料预览与后台读取明确分开；权限、下架和网络错误均不能改变数据来源。 */
export function createCapabilityReader(source: CapabilitySource, bundled: CapabilityManifestV2[], api: CapabilityApi) {
  function fromRecord(record: ManagedCapability): CapabilitySelection {
    const manifest = record?.manifest
    if (!manifest || validateCapabilityManifest(manifest).length) throw new Error('后台能力资料不完整，请联系维护人修正')
    if (record.status !== 'PUBLISHED' || manifest.governance.status !== 'PUBLISHED') throw new Error('该能力尚未发布或已停止发布')
    if (record.slug !== manifest.identity.slug || record.version !== manifest.identity.version || record.kind !== manifest.kind) {
      throw new Error('后台能力与版本资料不一致，请联系维护人检查')
    }
    return { manifest, source, record }
  }
  return {
    source,
    async list(kind: CapabilityKindV2): Promise<CapabilitySelection[]> {
      if (source === 'bundled') return bundled.filter(item => item.kind === kind && item.identity.slug !== 'platform-skill-adapter').map(manifest => ({ manifest, source }))
      const records = await api.capabilities({ kind })
      if (!Array.isArray(records)) throw new Error('后台能力目录响应无效')
      return records.map(fromRecord).filter(item => item.manifest.kind === kind && item.manifest.identity.slug !== 'platform-skill-adapter')
    },
    async get(slug: string): Promise<CapabilitySelection> {
      if (source === 'bundled') {
        const manifest = bundled.find(item => item.identity.slug === slug)
        if (!manifest) throw new Error('本地资料中没有这项能力')
        return { manifest, source }
      }
      const selected = fromRecord(await api.capability(slug))
      if (selected.manifest.identity.slug !== slug) throw new Error('后台返回的能力与当前入口不一致')
      return selected
    },
  }
}

export function capabilityErrorMessage(error: unknown): string {
  const response = (error as { response?: { status?: number } })?.response
  if (response?.status === 401) return '登录已失效，请重新登录后查看'
  if (response?.status === 403) return '你没有查看这项能力的权限'
  if (response?.status === 404) return '该能力不存在、尚未发布或已经下架'
  if (response || (error as { isAxiosError?: boolean })?.isAxiosError) return '能力服务暂时不可用，请稍后重试'
  return error instanceof Error ? error.message : '能力资料读取失败，请重试'
}
