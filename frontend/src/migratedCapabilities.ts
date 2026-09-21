import { validateCapabilityManifest, type CapabilityManifestV2 } from './capabilitySchema'

interface GeneratedCapability {
  generated: { source: string; packageContentSha256: string }
  manifest: unknown
}

const files = import.meta.glob('./generated/capabilities/*.json', { eager: true, import: 'default' }) as Record<string, GeneratedCapability>

export const bundledCapabilities = Object.entries(files).map(([path, value]) => {
  const errors = validateCapabilityManifest(value.manifest)
  if (errors.length) throw new Error(`统一能力清单校验失败：${path}：${errors.join('；')}`)
  return value.manifest as CapabilityManifestV2
})

export const bundledCapabilityArtifacts = new Map(Object.values(files).map(value => {
  const manifest = value.manifest as CapabilityManifestV2
  return [manifest.identity.slug, value.generated] as const
}))

export const migratedCapabilityBySlug = new Map(bundledCapabilities.map(manifest => [manifest.identity.slug, manifest]))
