<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { computed } from 'vue'
import PageHero from '../components/PageHero.vue'
import BaselineSourceNotice from '../components/BaselineSourceNotice.vue'
import { assets, stageByNumber, STAGE_NAMES } from '../data'
import { STAGE_COLORS } from '../constants'

const descriptions = ['周报、例会、回款跟踪、贯穿全程','项目启动、团队组建、计划制定','需求调研、用户需求、实施方案','技术选型、概要/详细设计、评审','编码规范、脚本审核','数据收集、迁移方案','测试计划、部署方案、性能测试','上线运维、培训、巡检','验收材料、专家评审、项目复盘']
const cards = computed(() => STAGE_NAMES.map((name, stage) => ({
  stage, name, description: stageByNumber(stage)?.description || descriptions[stage],
  count: assets.filter(a => a.stage_num === stage).length,
  templates: assets.filter(a => a.stage_num === stage && a.type === 'T').length,
  cases: assets.filter(a => a.stage_num === stage && a.type === 'C').length,
  tools: assets.filter(a => a.stage_num === stage && a.type === 'A').length,
})))
</script>

<template>
  <PageHero eyebrow="DELIVERY BASELINE" title="交付基线库" description="围绕项目交付全生命周期组织标准模板、卓越案例与 AI 辅助工具，按阶段快速定位可复用资产。" />
  <section class="section">
    <div class="container">
      <BaselineSourceNotice />
      <div class="section-heading"><div><h2>9 大交付环节</h2><p>从项目监控到总结验收，形成可追溯的能力资产链路。</p></div><el-tag type="info" size="large">共 {{ assets.length }} 项资产</el-tag></div>
      <div class="baseline-grid">
        <RouterLink v-for="item in cards" :key="item.stage" :to="`/stage/${item.stage}`" class="stage-card" :style="{ '--stage': STAGE_COLORS[item.stage] }">
          <div class="stage-number">{{ String(item.stage).padStart(2, '0') }}</div>
          <div class="stage-main"><span>STAGE {{ item.stage }}</span><h3>{{ item.name }}</h3><p>{{ item.description }}</p></div>
          <div class="stage-counts"><span><b>{{ item.templates }}</b> 模板</span><span><b>{{ item.cases }}</b> 案例</span><span><b>{{ item.tools }}</b> 工具</span></div>
          <div class="stage-total">{{ item.count }} <small>项资产</small><el-icon><ArrowRight /></el-icon></div>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.baseline-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.stage-card { min-height: 160px; display: grid; grid-template-columns: 74px 1fr auto; align-items: center; gap: 20px; padding: 22px; background: white; border: 1px solid var(--line); border-left: 4px solid var(--stage); border-radius: 6px; }
.stage-card:hover { box-shadow: var(--shadow); border-color: #cbd2dd; border-left-color: var(--stage); }
.stage-number { font-size: 34px; color: var(--stage); font-weight: 800; }
.stage-main span { font-size: 10px; color: var(--muted); font-weight: 700; }
.stage-main h3 { margin: 5px 0 7px; font-size: 19px; }
.stage-main p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.6; }
.stage-counts { display: flex; flex-direction: column; gap: 5px; color: var(--muted); font-size: 11px; }
.stage-counts b { color: var(--ink); }
.stage-total { grid-column: 2 / 4; display: flex; align-items: center; gap: 6px; padding-top: 13px; border-top: 1px solid var(--line); color: var(--stage); font-weight: 700; }
.stage-total small { flex: 1; color: var(--muted); font-weight: 400; }
@media (max-width: 850px) { .baseline-grid { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .stage-card { grid-template-columns: 52px 1fr; padding: 17px; gap: 12px; }.stage-number { font-size: 25px; }.stage-counts { grid-column: 2; flex-direction: row; flex-wrap: wrap; }.stage-total { grid-column: 1 / 3; } }
</style>
