import type { SkillProfile } from './skillCatalog'

export interface PackageFile { path: string; kind: '定义' | '手册' | '规则' | '模板' | '案例' | '资源' | '日志'; size: string; description: string }
export interface UsageRecord { id: string; project: string; team: string; environment: 'Codex' | 'WorkBuddy' | '本地'; date: string; output: string; status: '已完成' | '待确认' }
export interface SkillDeepDive {
  notFor: string[]; readiness: Array<{ title: string; detail: string }>
  qualityDetails: Array<{ name: string; focus: string; evidence: string }>
  riskLevels: Array<{ level: string; meaning: string; action: string }>
  handoff: Array<{ stage: string; owner: string; deliverable: string; gate: string }>
  faqs: Array<{ question: string; answer: string }>
}

export function packageFiles(_profile: SkillProfile): PackageFile[] {
  return [
    { path: 'SKILL.md', kind: '定义', size: '—', description: '入口说明与安全边界' },
    { path: 'manual.md', kind: '手册', size: '—', description: '中文操作手册' },
    { path: 'scripts/adapt.py', kind: '资源', size: '—', description: '静态检查和整理脚本' },
    { path: 'references/', kind: '规则', size: '—', description: '清单规范和平台接入规则' },
  ]
}

export function usageRecords(_profile: SkillProfile): UsageRecord[] { return [] }

export function skillDeepDive(profile: SkillProfile): SkillDeepDive {
  return {
    notFor: ['不能替代管理员审核、代码审查和业务验收', '不能执行来源不明的脚本', '不能把缺失事实自动补写为确定结论'],
    readiness: profile.inputs.map(item => ({ title: item.name, detail: item.detail })),
    qualityDetails: profile.qualityChecks.map(name => ({ name, focus: `检查“${name}”是否有材料依据。`, evidence: '检查报告、文件路径与哈希' })),
    riskLevels: [{ level: '停止', meaning: '发现疑似凭据、路径越界或加密归档', action: '停止整理并交由管理员处理' }, { level: '人工复核', meaning: '来源、授权或业务效果无法静态确认', action: '标记待确认，不自动发布' }],
    handoff: profile.workflow.map(item => ({ stage: item.title, owner: '能力维护人 / 平台管理员', deliverable: item.detail, gate: '人工确认后进入下一步' })),
    faqs: [{ question: '检查通过后会自动发布吗？', answer: '不会。输出始终是待审核资料，仍需管理员完成审核与发布。' }],
  }
}

export function rawSkillDefinition(profile: SkillProfile): string {
  const list = (items: string[]) => items.map(item => `- ${item}`).join('\n')
  return `---\nname: ${profile.slug}\nversion: ${profile.version}\ndescription: ${profile.description}\n---\n\n# ${profile.name}\n\n## 适用场景\n\n${list(profile.scenarios)}\n\n## 输入材料\n\n${list(profile.inputs.map(item => `${item.name}：${item.detail}`))}\n\n## 输出结果\n\n${list(profile.outputs.map(item => `${item.name}：${item.detail}`))}\n\n## 使用边界\n\n${list(profile.boundaries)}\n`
}
