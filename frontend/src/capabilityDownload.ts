import type { CapabilitySelection } from './capabilityAccess'

export interface LocalCapabilityPackage { slug: string; version: string; file: string; bytes: number; sha256: string }
export function capabilityDownloadAccess(selected: CapabilitySelection, packages: LocalCapabilityPackage[], localEnabled: boolean) {
  const { manifest, source, record } = selected
  const label = manifest.kind === 'skill' ? '下载 Skill 包' : '下载工具交付包'
  const blocked = (reason: string) => ({ label, reason, local: undefined as LocalCapabilityPackage | undefined })
  if (!manifest.delivery.package) return blocked('此工具仅提供在线体验，无独立交付包')
  if (manifest.governance.status === 'ARCHIVED') return blocked('此能力已归档，暂不提供下载')
  if (source === 'backend') {
    if (record?.status !== 'PUBLISHED' || manifest.governance.status !== 'PUBLISHED') return blocked('当前版本尚未发布')
    if (!record.packageFileId) return blocked('当前版本尚未上传交付包')
    if (record.slug !== manifest.identity.slug || record.version !== manifest.identity.version) return blocked('交付版本不一致，请重新读取')
    return blocked('')
  }
  if (!localEnabled) return blocked('此环境未提供本地交付包，请从后台发布后下载')
  const local = packages.find(item => item.slug === manifest.identity.slug && item.version === manifest.identity.version && /^[a-z0-9.-]+\.zip$/.test(item.file))
  return { label, reason: local ? '' : '当前未找到对应的交付包，请重新生成后刷新页面', local }
}
