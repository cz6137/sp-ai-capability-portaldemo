import type { SkillRecord, SkillVersion } from './types'
import type { CapabilityManifestBase } from './capabilityManifest'

export interface SkillProfile extends CapabilityManifestBase {
  kind: 'skill'; id: string; slug: string; name: string; shortName: string; tagline: string
  description: string; accent: string; version: string; updatedAt: string; maintainer: string
  stageIds: number[]; maturity: '待验证' | '试运行'; fileCount: number; packageSize: string
  tags: string[]; metrics: Array<{ value: string; label: string }>; scenarios: string[]; triggerWords: string[]
  inputs: Array<{ name: string; detail: string; formats?: string }>
  outputs: Array<{ name: string; detail: string; formats?: string }>
  workflow: Array<{ title: string; detail: string }>; packageItems: Array<{ name: string; detail: string; count?: string }>
  qualityChecks: string[]; boundaries: string[]; quickStart: string[]
  changelog: Array<{ version: string; date: string; note: string }>
}

/** 公开仓库仅保留平台接入适配 Skill，不包含业务案例 Skill。 */
export const skillProfiles: SkillProfile[] = [{
  schemaVersion: '2.1', kind: 'skill', id: 'platform-skill-adapter', slug: 'platform-skill-adapter',
  name: '适应平台 Skill', shortName: '平台接入适配', tagline: '检查外部能力资料，生成可预检的统一能力包',
  description: '静态检查外部能力资料，补齐统一清单并整理待审核交付目录；不执行外部脚本，不自动发布。',
  accent: '#a32d2d', version: '1.2.0', updatedAt: '2026-09-16', maintainer: '平台维护人（待指定）',
  stageIds: [0], maturity: '待验证', fileCount: 8, packageSize: '以实际构建为准', tags: ['统一接入', '静态检查', '中文能力包'],
  metrics: [{ value: '2.1', label: '清单规范' }, { value: '0', label: '外部脚本执行' }],
  scenarios: ['检查待接入的 Skill 或工具资料', '生成待管理员复核的统一能力清单和交付目录'],
  triggerWords: ['适应平台', '能力接入适配', '整理能力包'],
  inputs: [{ name: '原始能力材料', detail: '用户指定的目录或 ZIP', formats: '目录 / ZIP' }],
  outputs: [{ name: '能力接入草稿', detail: '带缺项说明的统一清单', formats: 'JSON' }, { name: '统一交付目录', detail: '静态检查通过后的待审核目录', formats: '目录' }],
  workflow: [{ title: '静态检查', detail: '检查路径、文件限制、疑似凭据和清单结构。' }, { title: '归纳资料', detail: '按材料依据整理使用场景、输入、流程、输出和边界。' }, { title: '人工复核', detail: '由管理员核对来源、授权、质量和发布条件。' }],
  packageItems: [{ name: 'SKILL.md', detail: '入口和安全边界' }, { name: 'scripts/adapt.py', detail: '静态检查和整理脚本' }, { name: 'references/', detail: '清单规范与接入规则' }],
  qualityChecks: ['文件一致性', '路径安全', '疑似凭据', '清单完整性', '来源与授权'],
  boundaries: ['不执行外部脚本或安装依赖。', '不复制密钥，不直接发布。', '静态检查不能替代代码审查、授权审查和业务验收。'],
  deployment: { available: true, modes: ['ZIP 能力包', '本地运行'], environment: 'Python 3.10+ 受控环境', guide: '先阅读 SKILL.md，再在原材料副本上执行 inspect、draft 或 prepare。' },
  quickStart: ['阅读 SKILL.md。', '使用 inspect 检查目录或 ZIP。', '根据检查结果生成草稿并由管理员复核。'],
  changelog: [{ version: '1.2.0', date: '2026-09-16', note: '按材料内容归纳统一清单，强化静态检查和人工复核边界。' }],
}]

export const skillProfileById = new Map(skillProfiles.map(profile => [profile.id, profile]))
export const skillProfileBySlug = new Map(skillProfiles.map(profile => [profile.slug, profile]))

export function profileToRecord(profile: SkillProfile): SkillRecord {
  return { id: profile.id, slug: profile.slug, name: profile.name, description: profile.description,
    ownerId: profile.maintainer, status: 'PUBLISHED', downloadCount: 0, sourceType: 'INTERNAL',
    caseText: profile.scenarios.join('\n'), usageGuide: profile.quickStart.join('\n'), enabled: true,
    stageIds: profile.stageIds, createdAt: profile.updatedAt, updatedAt: profile.updatedAt }
}

export function profileVersions(profile: SkillProfile): SkillVersion[] {
  return profile.changelog.map((item, index) => ({ id: `${profile.id}-v${index}`, skillId: profile.id,
    versionName: item.version, status: 'PUBLISHED', changeNote: item.note, createdAt: item.date }))
}

export function mergeCatalogSkills(apiSkills: SkillRecord[]): SkillRecord[] {
  const existing = new Set(apiSkills.map(skill => skill.slug))
  return [...skillProfiles.filter(profile => !existing.has(profile.slug)).map(profileToRecord), ...apiSkills]
}
