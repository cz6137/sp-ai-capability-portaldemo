import type { Asset, AssetType } from './types'

export interface MaterialSection { id: string; title: string; items: Asset[] }
export interface MaterialGroup { id: string; title: string; sections: MaterialSection[]; href?: string }
export interface StageOutline { title: string; sections: string[] }
export const materialTypes: Array<{ type: AssetType; label: string }> = [
  { type: 'T', label: 'SD 模板' }, { type: 'C', label: '卓越案例' }, { type: 'A', label: 'AI 工具' },
]

export function materialSections(items: Asset[], prefix: string): MaterialSection[] {
  const byName = new Map<string, Asset[]>()
  for (const item of items) {
    const name = item.sub_name || '其他材料'
    if (!byName.has(name)) byName.set(name, [])
    byName.get(name)!.push(item)
  }
  return [...byName].map(([title, items]) => ({ id: `${prefix}-section-${title}`, title, items }))
}

/** 旧版目录只提供展示层级；未匹配的新材料仍按原类别展示，不能丢失或重复计数。 */
export function stageMaterialGroups(items: Asset[], outline: StageOutline[], prefix: string): MaterialGroup[] {
  const remaining = new Map(materialSections(items, prefix).map(section => [section.title, section]))
  const groups: MaterialGroup[] = []
  for (const [index, entry] of outline.entries()) {
    const sections: MaterialSection[] = []
    for (const name of new Set([entry.title, ...entry.sections])) {
      const section = remaining.get(name)
      if (section) { sections.push(section); remaining.delete(name) }
    }
    if (sections.length) groups.push({ id: `${prefix}-group-${index}`, title: entry.title, sections })
  }
  for (const section of remaining.values()) groups.push({ id: `${section.id}-group`, title: section.title, sections: [section] })
  return groups
}
