import { apiGet, apiPost, apiPut, http } from './http'
import type { Asset, CaseCategory, ManagedSkill, SkillDetail, SkillForm, SkillRecord, StageSummary, Task, TaskStatus, UserSession } from '../types'
import type { CapabilityManifestV2 } from '../capabilitySchema'

export interface MeetingMinutesCapability {
  available: boolean
  transcriptionAvailable: boolean
  minutesAvailable: boolean
  status: 'READY' | 'NOT_CONFIGURED'
  message: string
  maxFileSizeMb: number
  acceptedExtensions: string[]
  externalProviders: string[]
  processingLocation: string
  retentionPolicy: string
}

export interface MeetingMinutesResult {
  summary: string
  keypoints: string[]
  decisions: string[]
  actions: string[]
}

export interface MeetingMinutesJob {
  id: string
  status: 'QUEUED' | 'TRANSCRIBING' | 'GENERATING' | 'COMPLETED' | 'FAILED'
  stage: string
  progress: number
  fileName: string
  mode: 'transcript' | 'minutes'
  error?: string
  transcript?: string
  result?: MeetingMinutesResult
}

export interface ManagedCapability {
  id: string
  versionId: string
  assetStatus: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'
  currentVersionId: string
  currentlyPublished: boolean
  reviewApproved: boolean
  pendingReviews?: number
  createdBy?: string
  /** 新版服务端可能直接提供提交人/提交时间；旧响应缺失时页面应保持可用。 */
  submitter?: string
  submitterId?: string
  submittedAt?: string
  slug: string
  kind: 'skill' | 'tool'
  name: string
  version: string
  status: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'
  packageFileId?: string
  manifest: CapabilityManifestV2
  createdAt: string
  updatedAt: string
}

export type CapabilityAction = 'submit' | 'approve' | 'reject' | 'publish' | 'unpublish' | 'archive'
export interface CapabilityAudit {
  id: string
  actorId: string
  action: string
  beforeData?: string
  afterData: string
  createdAt: string
}
export interface AdminCapabilityParams { status?: string; kind?: 'skill' | 'tool'; submitter?: string }
export interface AdminCapabilityPage { items: ManagedCapability[]; total: number; page?: number; pageSize?: number }

export const authApi = {
  login: (username: string, password: string) => apiPost<UserSession>('/auth/login', { username, password }),
  refresh: () => apiPost<UserSession>('/auth/refresh'),
  me: () => apiGet<UserSession>('/auth/me'),
  logout: () => apiPost<void>('/auth/logout'),
}

export const portalApi = {
  stages: () => apiGet<StageSummary[]>('/stages'),
  assets: (params?: { stage?: number; type?: string; q?: string }) => apiGet<Asset[]>('/assets', params),
  search: (q: string) => apiGet<{ assets: Asset[]; skills: SkillRecord[] }>('/search', { q }),
  skills: (params?: { stage?: number; q?: string }) => apiGet<SkillRecord[]>('/skills', params),
  skill: (id: string) => apiGet<SkillDetail>(`/skills/${id}`),
  managedSkills: () => apiGet<ManagedSkill[]>('/skills/manage'),
  saveSkill: (form: SkillForm) => form.id ? apiPut<SkillRecord>(`/skills/manage/${form.id}`, form) : apiPost<SkillRecord>('/skills/manage', form),
  archiveSkill: (id: string) => http.delete(`/skills/manage/${id}`),
  importSkill: async (file: File, metadata: SkillForm) => {
    const body = new FormData()
    body.append('file', file)
    body.append('metadata', JSON.stringify(metadata))
    return (await http.post('/skills/manage/import', body)).data.data as SkillRecord
  },
  downloadSkill: (id: string) => http.get(`/skills/${id}/download`, { responseType: 'blob' }),
  caseCategories: () => apiGet<CaseCategory[]>('/case-categories'),
  cases: (code: string) => apiGet<{ category: CaseCategory; items: Asset[] }>(`/case-categories/${code}`),
  tasks: (params?: Record<string, string | number>) => apiGet<{ items: Task[]; total: number }>('/tasks', params),
  updateProgress: (taskId: string, teamId: string, status: TaskStatus, lockVersion: number) =>
    apiPut(`/tasks/${taskId}/progress/${teamId}`, { status, lockVersion }),
  upload: async (file: File) => {
    const body = new FormData(); body.append('file', file)
    return (await http.post('/files/upload', body)).data.data
  },
  meetingMinutesCapabilities: () => apiGet<MeetingMinutesCapability>('/tools/meeting-minutes/capabilities'),
  startMeetingMinutesJob: async (file: File, context: { subject: string; attendees: string; background: string; template: string; mode: 'transcript' | 'minutes'; durationSeconds: number }) => {
    const body = new FormData()
    body.append('file', file)
    body.append('subject', context.subject)
    body.append('attendees', context.attendees)
    body.append('background', context.background)
    body.append('template', context.template)
    body.append('mode', context.mode)
    body.append('durationSeconds', String(context.durationSeconds))
    return (await http.post('/tools/meeting-minutes/jobs', body, { timeout: 120_000 })).data.data as MeetingMinutesJob
  },
  meetingMinutesJob: (id: string) => apiGet<MeetingMinutesJob>(`/tools/meeting-minutes/jobs/${id}`),
  adminCapabilities: async (params: AdminCapabilityParams = {}): Promise<AdminCapabilityPage> => {
    const result = await apiGet<ManagedCapability[] | AdminCapabilityPage>('/admin/capabilities', params)
    // 兼容旧服务端直接返回数组，以及分页对象（items/records 均可被渐进接入）。
    if (Array.isArray(result)) return { items: result, total: result.length }
    const items = Array.isArray(result?.items) ? result.items : Array.isArray((result as { records?: unknown }).records) ? (result as unknown as { records: ManagedCapability[] }).records : []
    return { ...result, items, total: typeof result?.total === 'number' ? result.total : items.length }
  },
  myCapabilities: () => apiGet<ManagedCapability[]>('/my/capabilities'),
  myCapabilityVersions: (id: string) => apiGet<ManagedCapability[]>(`/my/capabilities/${id}/versions`),
  myCapabilityAudits: (id: string, versionId: string) => apiGet<CapabilityAudit[]>(`/my/capabilities/${id}/versions/${versionId}/audits`),
  submitMyCapability: (record: ManagedCapability, reason: string) => apiPost<ManagedCapability>(`/my/capabilities/${record.id}/versions/${record.versionId}/submit`, { reason, expectedUpdatedAt: record.updatedAt }),
  downloadMyCapabilityVersion: (id: string, versionId: string) => http.get(`/my/capabilities/${id}/versions/${versionId}/download`, { responseType: 'blob' }),
  importMyCapability: async (manifest: CapabilityManifestV2, packageFile?: File, expectedUpdatedAt?: string) => {
    const body = new FormData(); body.append('manifest', JSON.stringify(manifest))
    if (packageFile) body.append('package', packageFile)
    if (expectedUpdatedAt) body.append('expectedUpdatedAt', expectedUpdatedAt)
    return (await http.post('/my/capabilities/import', body, { timeout: 120_000 })).data.data as ManagedCapability
  },
  capabilityVersions: (id: string) => apiGet<ManagedCapability[]>(`/admin/capabilities/${id}/versions`),
  capabilityAudits: (id: string, versionId: string) => apiGet<CapabilityAudit[]>(`/admin/capabilities/${id}/versions/${versionId}/audits`),
  capabilityAction: (record: ManagedCapability, action: CapabilityAction, reason: string) => apiPost<ManagedCapability>(
    `/admin/capabilities/${record.id}/versions/${record.versionId}/actions/${action}`, { reason, expectedUpdatedAt: record.updatedAt }),
  downloadCapabilityVersion: (id: string, versionId: string) => http.get(`/admin/capabilities/${id}/versions/${versionId}/download`, { responseType: 'blob' }),
  importCapability: async (manifest: CapabilityManifestV2, packageFile?: File, expectedUpdatedAt?: string) => {
    const body = new FormData()
    body.append('manifest', JSON.stringify(manifest))
    if (packageFile) body.append('package', packageFile)
    if (expectedUpdatedAt) body.append('expectedUpdatedAt', expectedUpdatedAt)
    return (await http.post('/admin/capabilities/import', body, { timeout: 120_000 })).data.data as ManagedCapability
  },
  capabilities: (params?: { kind?: 'skill' | 'tool' }) => apiGet<ManagedCapability[]>('/capabilities', params),
  capability: (slug: string) => apiGet<ManagedCapability>(`/capabilities/${slug}`),
  downloadCapability: (slug: string) => http.get(`/capabilities/${slug}/download`, { responseType: 'blob' }),
}
