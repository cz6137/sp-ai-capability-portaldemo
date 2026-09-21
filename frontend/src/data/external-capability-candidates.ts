import type { CapabilityKindV2 } from '../capabilitySchema'

export interface ExternalCapabilityCandidate {
  kind: CapabilityKindV2
  name: string
  summary: string
  source: string
  sourceUrl: string
  license: string
  tags: string[]
  actionLabel: string
  statusLabel: string
}

// 已选的 Word、Excel、PPTX、OCR 与语音转写候选项已转为平台本地能力。
export const externalCapabilityCandidates: ExternalCapabilityCandidate[] = []
