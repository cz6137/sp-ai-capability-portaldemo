import snapshotRaw from './data/baseline-snapshot-0815.json'
import tasksRaw from './data/demo-tasks.json'
import { reactive } from 'vue'
import { portalApi } from './api/portal'
import type { Asset, StageSummary, Task } from './types'
import { createBaselineReader, resolveBaselineSource, summarizeBaseline, type BaselineSnapshot } from './baselineAccess'
import { capabilityErrorMessage } from './capabilityAccess'

export { STAGE_NAMES } from './baselineAccess'
const snapshot = snapshotRaw as BaselineSnapshot
const source = resolveBaselineSource(import.meta.env.VITE_BASELINE_SOURCE, import.meta.env.DEV)
const reader = createBaselineReader(source, snapshot, portalApi)
export const stages = reactive<StageSummary[]>(source === 'snapshot' ? summarizeBaseline(snapshot.files) : [])
export const assets = reactive<Asset[]>(source === 'snapshot' ? [...snapshot.files] : [])
export const baselineState = reactive({ source, sourceLabel: snapshot.sourceLabel, capturedAt: snapshot.capturedAt, loading: false, error: '' })
export const tasks = tasksRaw as Task[]

let requestId = 0
export async function hydratePortalData() {
  const current = ++requestId
  baselineState.loading = true; baselineState.error = ''
  try {
    const next = await reader.read()
    if (current !== requestId) return
    stages.splice(0, stages.length, ...next.stages)
    assets.splice(0, assets.length, ...next.assets)
  } catch (cause) {
    if (current === requestId) {
      stages.splice(0); assets.splice(0)
      baselineState.error = `基线目录读取失败：${capabilityErrorMessage(cause)}`
    }
    throw cause
  } finally { if (current === requestId) baselineState.loading = false }
}

export const stageByNumber = (stageNumber: number) => {
  return stages.find((item) => item.stage === stageNumber)
}

export const assetsByStage = (stageNumber: number) => assets.filter((item) => item.stage_num === stageNumber)
