export interface CapabilityMetrics {
  experiences: number
  downloads: number
}

// 首版展示数据。接入统计接口时保留此类型，只替换数据读取来源。
export const capabilityMetrics: Readonly<Record<string, CapabilityMetrics>> = {
  'platform-skill-adapter': { experiences: 0, downloads: 139 },
  'document-converter': { experiences: 1286, downloads: 342 },
  'image-ocr': { experiences: 963, downloads: 218 },
  'meeting-minutes': { experiences: 746, downloads: 185 },
}

export function metricsOf(slug: string): CapabilityMetrics {
  return capabilityMetrics[slug] || { experiences: 0, downloads: 0 }
}
