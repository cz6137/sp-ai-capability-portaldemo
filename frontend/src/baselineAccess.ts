import type { Asset, StageSummary } from './types'

export type BaselineSource = 'snapshot' | 'backend'
export interface BaselineSnapshot { sourceLabel: string; capturedAt: string; files: Asset[] }
export const STAGE_NAMES = ['项目监控', '预投立项', '需求分析', '系统设计', '编码实现', '数据处理', '部署测试', '运行维护', '总结验收'] as const
const descriptions = ['周报、例会、回款跟踪、贯穿全程', '项目启动、团队组建、计划制定', '需求调研、用户需求、实施方案', '技术选型、概要/详细设计、评审', '编码规范、脚本审核', '数据收集、迁移方案', '测试计划、部署方案、性能测试', '上线运维、培训、巡检', '验收材料、专家评审、项目复盘']

export function resolveBaselineSource(value: string | undefined, development: boolean): BaselineSource {
  const source = value || (development ? 'snapshot' : 'backend')
  if (source !== 'snapshot' && source !== 'backend') throw new Error('基线目录数据来源配置无效')
  return source
}

export function summarizeBaseline(files: Asset[]): StageSummary[] {
  return STAGE_NAMES.map((name, stage) => ({
    stage, name: `${stage}-${name}`, description: descriptions[stage], subcategories: [],
    counts: {
      sd_template: files.filter(item => item.stage_num === stage && item.type === 'T').length,
      case: files.filter(item => item.stage_num === stage && item.type === 'C').length,
      ai_tool: files.filter(item => item.stage_num === stage && item.type === 'A').length,
    },
  }))
}

export function createBaselineReader(source: BaselineSource, snapshot: BaselineSnapshot, api: {
  stages(): Promise<StageSummary[]>; assets(): Promise<Asset[]>
}) {
  return {
    source,
    async read() {
      if (source === 'snapshot') return { stages: summarizeBaseline(snapshot.files), assets: [...snapshot.files] }
      const [stages, assets] = await Promise.all([api.stages(), api.assets()])
      if (!Array.isArray(stages) || !Array.isArray(assets)
        || stages.some(item => !item || !Number.isInteger(item.stage) || typeof item.name !== 'string')
        || assets.some(item => !item || !Number.isInteger(item.stage_num) || typeof item.name !== 'string' || !['T', 'C', 'A'].includes(item.type))) {
        throw new Error('后台基线目录格式不完整，请联系维护人检查')
      }
      return { stages, assets }
    },
  }
}
