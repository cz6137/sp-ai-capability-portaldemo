export type AssetType = 'T' | 'C' | 'A'

export interface Asset {
  id?: string
  name: string
  type: AssetType
  stage_num: number
  stage_name: string
  sub_name: string
  sub_number: string
  media_id?: string | null
  folder_id?: string | null
  direct_url?: string | null
  ima_url?: string | null
  file_id?: string | null
  source_url?: string | null
  source_type?: 'INTERNAL' | 'BOOTSTRAP_IMA' | 'IMA_SERVER' | 'IMA_BRIDGE' | string
  source_metadata?: Record<string, unknown> | string | null
  last_synced_at?: string | null
  case_category_id?: string | null
}

export interface StageSummary {
  stage: number
  name: string
  description: string
  counts: { sd_template: number; case: number; ai_tool: number }
  subcategories: Array<{
    number: string
    name: string
    folder_id: string
    files: Array<{ name: string; type: AssetType }>
    counts?: number
  }>
}

export interface Task {
  uuid?: string
  id: string | number
  stage: string
  scene: string
  ai: string
  desc: string
  input: string
  output: string
  type: 'Skill' | '工具'
  skill: string
  pri: '高' | '中' | '低'
  diff: '高' | '中' | '低'
  t1s: string
  t2s: string
  t3s: string
  progress?: Array<{ teamId: string; status: TaskStatus; lockVersion: number }>
}

export type TaskStatus = '待创建' | '进行中' | '已完成'

export interface UserSession {
  id: string
  username: string
  displayName: string
  teamId?: string
  roles: Array<{ authority: string }>
  accessToken?: string
}

export interface SkillRecord {
  id: string
  slug: string
  name: string
  description?: string
  ownerId?: string
  status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED'
  downloadCount: number
  sourceType: 'INTERNAL' | 'EXTERNAL'
  caseText?: string
  usageGuide?: string
  enabled: boolean
  stageIds: number[]
  createdAt: string
  updatedAt: string
}

export interface SkillVersion {
  id: string
  skillId: string
  versionName: string
  fileId?: string
  status: SkillRecord['status']
  changeNote?: string
  reviewNote?: string
  createdAt: string
}

export interface SkillDetail {
  skill: SkillRecord
  versions: SkillVersion[]
  downloadAvailable: boolean
}

export interface ManagedSkill {
  skill: SkillRecord
  latestVersion?: SkillVersion
}

export interface SkillForm {
  id?: string
  slug: string
  name: string
  description: string
  sourceType: 'INTERNAL' | 'EXTERNAL'
  caseText: string
  usageGuide: string
  stageIds: number[]
  versionName: string
  changeNote: string
}

export interface CaseCategory {
  id: string
  code: string
  name: string
  description: string
  externalFolderId?: string
  count: number
  examples: string[]
}
