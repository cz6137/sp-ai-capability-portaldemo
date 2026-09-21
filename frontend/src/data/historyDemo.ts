/** 公开 Demo 使用的虚构版本轨迹，不对应任何真实项目或人员。 */
export const HISTORY_DEMO = true

export interface HistoryVersion { version: string; status: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'; assetStatus: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'; current: boolean; steps: HistoryStep[] }
export interface HistoryStep { dayOffset: number; time: string; action: string; label: string; actor: string; tone: 'draft' | 'warn' | 'ok' | 'brand' | 'retire'; reason: string; effect: string }
export interface HistoryRecord { slug: string; kind: 'skill' | 'tool' | 'knowledge'; name: string; versions: HistoryVersion[] }
export type DemoLifecycleStatus = 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'DELISTED' | 'ARCHIVED' | 'REJECTED'
export interface HistoryEvent extends HistoryStep { version: string; at: string }

const BASE_DATE = '2026-09-14'
const step = (dayOffset: number, time: string, action: string, label: string, actor: string, tone: HistoryStep['tone'], reason: string, effect: string): HistoryStep => ({ dayOffset, time, action, label, actor, tone, reason, effect })

export function nextDemoVersion(version: string): string {
  const parts = version.split('.')
  for (let index = parts.length - 1; index >= 0; index--) {
    if (!/^\d+$/.test(parts[index])) continue
    parts[index] = String(Number(parts[index]) + 1)
    return parts.join('.')
  }
  return `${version}.1`
}

function previousDemoVersion(version: string): string {
  const parts = version.split('.')
  for (let index = parts.length - 1; index >= 0; index--) {
    if (!/^\d+$/.test(parts[index]) || Number(parts[index]) <= 0) continue
    parts[index] = String(Number(parts[index]) - 1)
    return parts.join('.')
  }
  return `${version}-previous`
}

function publishedSteps(slug: string, version: string, submitter: string): HistoryStep[] {
  return [
    step(42, '09:00', 'CAPABILITY_IMPORT', '保存草稿', submitter, 'draft', `录入 ${version} 清单与交付资料。`, `target_id=${slug}:${version}`),
    step(40, '10:20', 'CAPABILITY_SUBMIT', '提交审核', submitter, 'warn', '资料补充完整，提交管理员审核。', `target_id=${slug}:${version}`),
    step(38, '14:00', 'CAPABILITY_APPROVE', '审核通过', '演示审批员', 'ok', '清单与交付包一致。', 'reviewer=demo-reviewer'),
    step(38, '15:00', 'CAPABILITY_PUBLISH', '上架', '演示发布员', 'ok', '发布为当前版本。', `current_version_id=${version}`),
  ]
}

export function buildDemoHistory(input: { slug: string; kind: HistoryRecord['kind']; name: string; version: string; publishedVersion?: string; status: DemoLifecycleStatus; submitter: string }): HistoryRecord {
  const publishedVersion = input.publishedVersion ?? (input.status === 'PUBLISHED' || input.status === 'DELISTED' ? input.version : previousDemoVersion(input.version))
  const steps = [
    step(6, '09:10', 'CAPABILITY_IMPORT', '保存草稿', input.submitter, 'draft', `录入 ${input.version} 清单与交付资料。`, `target_id=${input.slug}:${input.version}`),
    step(5, '10:30', 'CAPABILITY_SUBMIT', '提交审核', input.submitter, 'warn', '提交管理员审核。', `target_id=${input.slug}:${input.version}`),
  ]
  if (input.status === 'REJECTED') steps.push(step(3, '14:20', 'CAPABILITY_REJECT', '退回修改', '演示审批员', 'retire', '资料需补充。', 'version.status=DRAFT'))
  if (['APPROVED', 'PUBLISHED', 'DELISTED', 'ARCHIVED'].includes(input.status)) steps.push(step(3, '14:20', 'CAPABILITY_APPROVE', '审核通过', '演示审批员', 'ok', '同意进入发布环节。', 'reviewer=demo-reviewer'))
  if (['PUBLISHED', 'DELISTED'].includes(input.status)) steps.push(step(2, '10:00', 'CAPABILITY_PUBLISH', '上架', '演示发布员', 'ok', '发布到前台。', `current_version_id=${input.version}`))
  if (input.status === 'DELISTED') steps.push(step(1, '15:10', 'CAPABILITY_UNPUBLISH', '下架', '演示发布员', 'brand', '停止前台使用。', 'asset.status=ARCHIVED'))
  if (input.status === 'ARCHIVED') steps.push(step(0, '16:20', 'CAPABILITY_ARCHIVE', '归档', '演示发布员', 'retire', '升级终止并归档。', 'version.status=ARCHIVED'))
  const status: HistoryVersion['status'] = input.status === 'REJECTED' ? 'DRAFT' : input.status === 'IN_REVIEW' || input.status === 'APPROVED' ? 'IN_REVIEW' : input.status === 'ARCHIVED' ? 'ARCHIVED' : 'PUBLISHED'
  const assetStatus: HistoryVersion['assetStatus'] = input.status === 'DELISTED' ? 'ARCHIVED' : 'PUBLISHED'
  const versions: HistoryVersion[] = [{ version: input.version, status, assetStatus, current: input.status === 'PUBLISHED', steps }]
  if (input.version !== publishedVersion) versions.push({ version: publishedVersion, status: 'PUBLISHED', assetStatus: 'PUBLISHED', current: true, steps: publishedSteps(input.slug, publishedVersion, input.submitter) })
  else if (input.status === 'PUBLISHED' || input.status === 'DELISTED') {
    const previousVersion = previousDemoVersion(input.version)
    versions.push({ version: previousVersion, status: 'ARCHIVED', assetStatus, current: false,
      steps: [...publishedSteps(input.slug, previousVersion, input.submitter), step(8, '11:10', 'CAPABILITY_ARCHIVE', '归档', '演示发布员', 'retire', `已由 ${input.version} 取代。`, 'version.status=ARCHIVED')] })
  }
  return { slug: input.slug, kind: input.kind, name: input.name, versions }
}

export const historyDemoRecords: HistoryRecord[] = [buildDemoHistory({ slug: 'platform-skill-adapter', kind: 'skill', name: '适应平台 Skill', version: '1.2.0', status: 'PUBLISHED', submitter: '演示员工' })]

const shiftDay = (days: number) => { const date = new Date(`${BASE_DATE}T00:00:00Z`); date.setUTCDate(date.getUTCDate() - days); return date.toISOString().slice(0, 10) }
export function historyEvents(record: HistoryRecord): HistoryEvent[] { return record.versions.flatMap(version => version.steps.map(item => ({ ...item, version: version.version, at: `${shiftDay(item.dayOffset)} ${item.time}` }))).sort((a, b) => b.at.localeCompare(a.at)) }
export function versionUpdatedAt(record: HistoryRecord, version: string): string { return historyEvents(record).find(event => event.version === version)?.at ?? '—' }
export function assetUpdatedAt(record: HistoryRecord): string { return historyEvents(record)[0]?.at ?? '—' }
export function historyForAsset(slug: string): HistoryRecord | undefined { return historyDemoRecords.find(item => item.slug === slug) }
