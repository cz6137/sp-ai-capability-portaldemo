import { portalApi } from './api/portal'
import { bundledCapabilities } from './migratedCapabilities'
import { createCapabilityReader, resolveCapabilitySource } from './capabilityAccess'

export const capabilityReader = createCapabilityReader(
  resolveCapabilitySource(import.meta.env.VITE_CAPABILITY_SOURCE, import.meta.env.DEV),
  bundledCapabilities,
  portalApi,
)
