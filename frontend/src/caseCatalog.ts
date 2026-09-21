import type { Asset } from './types'

export const CASE_DOMAINS = [
  { code: 'real-estate', name: '不动产与地籍', description: '不动产登记、地籍调查及相关业务交付案例。' },
  { code: 'planning-government', name: '空间规划与政务', description: '空间规划、自然资源管理及政务服务交付案例。' },
  { code: 'data-foundation', name: '数据底座', description: '数据平台、基础支撑与数据服务交付案例。' },
  { code: 'housing', name: '住建业务', description: '住房、城乡建设及相关业务交付案例。' },
  { code: 'data-bureau', name: '数据局业务', description: '数据局相关业务与数据治理交付案例。' },
  { code: 'ai', name: 'AI+', description: '人工智能应用相关交付案例。' },
  { code: 'general', name: '通用/跨域', description: '通用交付材料、跨领域案例与待分类案例。' },
] as const
export interface CaseDomainTag { domain: string; source?: string; tagged_at?: string; tagger?: string }
/** 标签独立维护；只统计实际 C 类资产，不能用标签数量替代案例数量。 */
export function buildCaseCatalog(assets: Asset[], tags: Record<string, CaseDomainTag>) {
  const groups = CASE_DOMAINS.map(domain => ({ ...domain, items: [] as Asset[], unclassified: 0 }))
  for (const asset of assets) {
    if (asset.type !== 'C') continue
    const domain = asset.media_id ? tags[asset.media_id]?.domain : undefined
    const matched = groups.find(group => group.name === domain)
    const group = matched || groups[groups.length - 1]!
    group.items.push(asset)
    if (!matched) group.unclassified++
  }
  return groups.map(group => ({
    ...group, count: group.items.length,
    stageCount: new Set(group.items.map(item => item.stage_num)).size,
  }))
}
