<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Refresh, Filter, Document, TopRight, View } from '@element-plus/icons-vue'
import PageHero from '../components/PageHero.vue'
import { tasks as fallbackTasks } from '../data'
import { portalApi } from '../api/portal'
import { STAGE_COLORS, STAGE_ORDER, TASK_SHEET_URL, TEAMS } from '../constants'
import { useAuthStore } from '../stores/auth'
import type { Task, TaskStatus } from '../types'

type FilterKey = 'stage' | 'scene' | 'output' | 'skill' | 'pri' | 'diff' | 'type'
const statuses: TaskStatus[] = ['待创建', '进行中', '已完成']
const tasks = ref<Task[]>([])
const filters = reactive<Record<FilterKey, string>>({ stage: '', scene: '', output: '', skill: '', pri: '', diff: '', type: '' })
const page = ref(1)
const pageSize = ref(20)
const detail = ref<Task | null>(null)
const detailOpen = computed({
  get: () => detail.value !== null,
  set: (open: boolean) => { if (!open) detail.value = null },
})
const progress = ref<Record<string, { status: TaskStatus; lockVersion: number }>>({})
const teamIds = ['00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003']
const auth = useAuthStore()

const filterDefs: Array<{ key: FilterKey; label: string }> = [
  { key: 'stage', label: '交付阶段' }, { key: 'scene', label: '交付场景' }, { key: 'output', label: 'SD 产出物' },
  { key: 'skill', label: 'Skill / 工具' }, { key: 'pri', label: '优先级' }, { key: 'diff', label: '实现难度' }, { key: 'type', label: '载体类型' },
]

function statusOf(task: Task, teamIndex: number): TaskStatus {
  const saved = progress.value[`${task.id}_${teamIndex}`]
  if (saved) return saved.status
  const source = [task.t1s, task.t2s, task.t3s][teamIndex]
  return statuses.includes(source as TaskStatus) ? source as TaskStatus : '待创建'
}
async function cycleStatus(task: Task, teamIndex: number) {
  if (!task.uuid) return
  const current = statusOf(task, teamIndex)
  const next = statuses[(statuses.indexOf(current) + 1) % statuses.length]
  const key = `${task.id}_${teamIndex}`
  try {
    const saved: any = await portalApi.updateProgress(task.uuid, teamIds[teamIndex], next, progress.value[key]?.lockVersion || 0)
    progress.value = { ...progress.value, [key]: { status: saved.status, lockVersion: saved.lockVersion } }
    ElMessage.success(`任务 ${task.id} · ${TEAMS[teamIndex]}：${next}`)
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || '状态更新失败，请刷新后重试') }
}

async function loadTasks() {
  try {
    const result = await portalApi.tasks({ page: 0, size: 200 })
    tasks.value = result.items
    const next: Record<string, { status: TaskStatus; lockVersion: number }> = {}
    result.items.forEach(task => task.progress?.forEach(item => {
      const index = teamIds.indexOf(item.teamId)
      if (index >= 0) next[`${task.id}_${index}`] = { status: item.status, lockVersion: item.lockVersion }
    }))
    progress.value = next
  } catch { tasks.value = fallbackTasks.map(item => ({ ...item })); ElMessage.warning('后端不可用，当前为只读快照') }
}
onMounted(loadTasks)

const filteredTasks = computed(() => tasks.value.filter(task => filterDefs.every(({ key }) => !filters[key] || String(task[key]) === filters[key])))
const pagedTasks = computed(() => filteredTasks.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const activeFilterCount = computed(() => Object.values(filters).filter(Boolean).length)
watch([() => ({ ...filters }), pageSize], () => { page.value = 1 }, { deep: true })

function optionValues(currentKey: FilterKey) {
  const source = tasks.value.filter(task => filterDefs.every(({ key }) => key === currentKey || !filters[key] || String(task[key]) === filters[key]))
  const counts = new Map<string, number>()
  source.forEach(task => { const value = String(task[currentKey] || ''); if (value) counts.set(value, (counts.get(value) || 0) + 1) })
  const order = currentKey === 'stage' ? STAGE_ORDER : currentKey === 'pri' || currentKey === 'diff' ? ['高','中','低'] : currentKey === 'type' ? ['Skill','工具'] : []
  return [...counts.entries()].sort(([a], [b]) => order.length ? order.indexOf(a) - order.indexOf(b) : a.localeCompare(b, 'zh-CN')).map(([value, count]) => ({ value, count }))
}
function clearFilters() { filterDefs.forEach(({ key }) => { filters[key] = '' }) }

const totalStats = computed(() => ({
  total: tasks.value.length,
  high: tasks.value.filter(t => t.pri === '高').length,
  hard: tasks.value.filter(t => t.diff === '高').length,
  skill: tasks.value.filter(t => t.type === 'Skill').length,
  tool: tasks.value.filter(t => t.type === '工具').length,
  done: TEAMS.reduce((sum, _, index) => sum + tasks.value.filter(t => statusOf(t, index) === '已完成').length, 0),
}))
const teamStats = computed(() => TEAMS.map((name, index) => {
  const done = tasks.value.filter(t => statusOf(t, index) === '已完成').length
  const doing = tasks.value.filter(t => statusOf(t, index) === '进行中').length
  return { name, done, doing, percent: Math.round(done / tasks.value.length * 100) }
}))
const stageStats = computed(() => STAGE_ORDER.map((name, index) => ({ name, label: name.replace(/^\d+-/, ''), count: tasks.value.filter(t => t.stage === name).length, color: STAGE_COLORS[index] })).filter(i => i.count))
const maxStageCount = computed(() => Math.max(...stageStats.value.map(i => i.count), 1))
const distribution = computed(() => ({
  priority: ['高','中','低'].map((name, index) => ({ name, count: tasks.value.filter(t => t.pri === name).length, color: ['#dc2626','#d97706','#64748b'][index] })),
  difficulty: ['高','中','低'].map((name, index) => ({ name, count: tasks.value.filter(t => t.diff === name).length, color: ['#dc2626','#d97706','#16a34a'][index] })),
  type: ['Skill','工具'].map((name, index) => ({ name, count: tasks.value.filter(t => t.type === name).length, color: ['#2563eb','#7c3aed'][index] })),
}))
function conic(items: Array<{ count: number; color: string }>) {
  const total = items.reduce((sum, item) => sum + item.count, 0) || 1
  let cursor = 0
  return `conic-gradient(${items.map(item => { const start = cursor; cursor += item.count / total * 100; return `${item.color} ${start}% ${cursor}%` }).join(',')})`
}
function stageColor(stage: string) { return STAGE_COLORS[Math.max(0, STAGE_ORDER.indexOf(stage))] }

async function resetProgress() {
  try {
    await ElMessageBox.confirm('确定清除本机保存的全部进展状态，并恢复任务快照中的初始状态吗？', '重置进展', { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' })
    await loadTasks()
    ElMessage.success(`已重置并重新载入 ${tasks.value.length} 项任务`)
  } catch { /* user cancelled */ }
}
async function exportProgress() {
  const entries = Object.entries(progress.value)
  if (!entries.length) { ElMessage.info('暂无进展变更可导出'); return }
  const lines = ['序号\t交付阶段\t交付场景\tSD产出物\tSkill/工具\t团队\t当前状态']
  entries.forEach(([key, saved]) => {
    const match = key.match(/^(.+)_(\d)$/); if (!match) return
    const task = tasks.value.find(item => String(item.id) === match[1]); if (!task) return
    lines.push(`${task.id}\t${task.stage}\t${task.scene}\t${task.output}\t${task.skill}\t${TEAMS[Number(match[2])]}\t${saved.status}`)
  })
  try { await navigator.clipboard.writeText(lines.join('\n')); ElMessage.success(`已复制 ${lines.length} 行进展清单`) }
  catch { ElMessage.error('复制失败，请检查浏览器剪贴板权限') }
}
</script>

<template>
  <PageHero eyebrow="SKILL DELIVERY WORKBENCH" title="Skill 构建工作台" description="统一跟踪华南交付团队的 Skill 与工具构建任务，管理优先级、实现难度和三支团队的交付进展。">
    <div class="workbench-links"><RouterLink to="/skill-guide"><el-icon><Document /></el-icon>构建指南</RouterLink><a :href="TASK_SHEET_URL" target="_blank"><el-icon><TopRight /></el-icon>腾讯文档源表</a></div>
  </PageHero>

  <section class="workbench-section"><div class="container workbench-container">
    <div class="stat-grid">
      <div><span>任务总数</span><b>{{ totalStats.total }}</b></div><div><span>高优先级</span><b>{{ totalStats.high }}</b></div><div><span>高难度</span><b>{{ totalStats.hard }}</b></div><div><span>Skill</span><b>{{ totalStats.skill }}</b></div><div><span>工具</span><b>{{ totalStats.tool }}</b></div><div class="stat-done"><span>累计完成</span><b>{{ totalStats.done }}</b></div>
    </div>

    <div class="team-grid"><article v-for="(team,index) in teamStats" :key="team.name"><header><span>团队 {{ index + 1 }}</span><b>{{ team.name }}</b></header><el-progress :percentage="team.percent" :stroke-width="8"/><footer>已完成 <b>{{ team.done }}</b> / {{ tasks.length }} · 进行中 <b>{{ team.doing }}</b></footer></article></div>

    <div class="charts-grid">
      <section class="chart-panel surface stage-chart"><h3>任务阶段分布</h3><div class="bars"><div v-for="item in stageStats" :key="item.name"><span>{{ item.label }}</span><i><b :style="{ width: `${item.count / maxStageCount * 100}%`, background: item.color }"></b></i><em>{{ item.count }}</em></div></div></section>
      <section v-for="(items,key) in distribution" :key="key" class="chart-panel surface donut-chart"><h3>{{ key === 'priority' ? '优先级' : key === 'difficulty' ? '实现难度' : '载体类型' }}</h3><div class="donut" :style="{ background: conic(items) }"><span>{{ items.reduce((s,i)=>s+i.count,0) }}<small>总数</small></span></div><div class="legend"><span v-for="item in items" :key="item.name"><i :style="{ background: item.color }"></i>{{ item.name }} {{ item.count }}</span></div></section>
    </div>

    <section class="task-panel surface">
      <header class="task-header"><div><h2>任务清单</h2><p>点击状态可按「待创建 → 进行中 → 已完成」循环切换</p></div><div class="task-actions"><el-button :icon="CopyDocument" @click="exportProgress">导出进展</el-button><el-button :icon="Refresh" @click="resetProgress">重置进展</el-button></div></header>
      <div class="filter-grid">
        <el-select v-for="def in filterDefs" :key="def.key" v-model="filters[def.key]" clearable :placeholder="def.label" :aria-label="def.label">
          <el-option v-for="option in optionValues(def.key)" :key="option.value" :label="`${option.value} (${option.count})`" :value="option.value" />
        </el-select>
        <el-button v-if="activeFilterCount" :icon="Filter" @click="clearFilters">清除筛选 ({{ activeFilterCount }})</el-button>
        <span class="filter-count">显示 {{ filteredTasks.length }} / {{ tasks.length }}</span>
      </div>
      <el-table :data="pagedTasks" stripe border table-layout="fixed">
        <el-table-column prop="id" label="序号" width="66" fixed />
        <el-table-column label="交付阶段" width="130"><template #default="{row}"><span class="stage-label"><i :style="{background:stageColor(row.stage)}"></i>{{ row.stage }}</span></template></el-table-column>
        <el-table-column prop="scene" label="交付场景" width="150" show-overflow-tooltip />
        <el-table-column prop="output" label="SD 产出物" min-width="180" show-overflow-tooltip />
        <el-table-column prop="skill" label="Skill / 工具" min-width="190" show-overflow-tooltip />
        <el-table-column prop="ai" label="AI 辅助场景" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" width="78"><template #default="{row}"><el-tag size="small" :type="row.pri==='高'?'danger':row.pri==='中'?'warning':'info'">{{ row.pri }}</el-tag></template></el-table-column>
        <el-table-column label="难度" width="72"><template #default="{row}"><el-tag size="small" :type="row.diff==='高'?'danger':row.diff==='中'?'warning':'success'">{{ row.diff }}</el-tag></template></el-table-column>
        <el-table-column prop="type" label="载体" width="74" />
        <el-table-column v-for="(_,teamIndex) in TEAMS" :key="teamIndex" :label="`团队 ${teamIndex+1}`" width="105" fixed="right"><template #default="{row}"><button type="button" class="status-button" :class="`status-${statusOf(row,teamIndex)}`" @click.stop="cycleStatus(row,teamIndex)">{{ statusOf(row,teamIndex) }}</button></template></el-table-column>
        <el-table-column label="详情" width="62" fixed="right"><template #default="{row}"><el-button circle text :icon="View" title="查看详情" @click="detail=row" /></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" :total="filteredTasks.length" /></div>
    </section>
    <p class="source-note">数据来源：<a :href="TASK_SHEET_URL" target="_blank">交付 Skill 构建任务清单（腾讯文档）</a> · 平台任务 {{ tasks.length }} 项 · 进度由服务端统一管理</p>
  </div></section>

  <el-drawer v-model="detailOpen" :title="detail ? `任务 ${detail.id} 详情` : '任务详情'" size="min(92vw, 520px)">
    <div v-if="detail" class="detail-list"><label>交付阶段</label><p>{{ detail.stage }}</p><label>交付场景</label><p>{{ detail.scene }}</p><label>AI 辅助场景</label><p>{{ detail.ai }}</p><label>能力描述</label><p>{{ detail.desc }}</p><label>输入</label><p>{{ detail.input }}</p><label>输出（IMA）</label><p>{{ detail.output }}</p><label>Skill / 工具名称</label><p><code>{{ detail.skill }}</code></p><label>属性</label><p>{{ detail.type }} · {{ detail.pri }}优先级 · {{ detail.diff }}难度</p></div>
  </el-drawer>
</template>

<style scoped>
.workbench-links{display:flex;gap:10px;margin-top:22px}.workbench-links a{display:flex;align-items:center;gap:6px;padding:8px 11px;border:1px solid #64748b;border-radius:5px;color:#e2e8f0;font-size:12px}.workbench-section{padding:30px 0 48px}.workbench-container{width:min(1500px,calc(100% - 30px))}.stat-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:10px}.stat-grid>div{padding:16px 18px;background:white;border:1px solid var(--line);border-top:3px solid #64748b;border-radius:5px;display:flex;flex-direction:column;gap:5px}.stat-grid span{color:var(--muted);font-size:11px}.stat-grid b{font-size:25px}.stat-grid .stat-done{border-top-color:#16a34a}.stat-done b{color:#15803d}.team-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:12px}.team-grid article{padding:16px;background:white;border:1px solid var(--line);border-radius:5px}.team-grid header{display:flex;align-items:center;gap:8px;margin-bottom:14px}.team-grid header span{padding:3px 6px;background:#e8edf4;border-radius:3px;color:#64748b;font-size:10px}.team-grid header b{font-size:13px}.team-grid footer{margin-top:8px;color:var(--muted);font-size:11px}.team-grid footer b{color:var(--ink)}.charts-grid{display:grid;grid-template-columns:1.8fr repeat(3,1fr);gap:10px;margin-top:12px}.chart-panel{padding:16px;min-height:250px}.chart-panel h3{margin:0 0 14px;font-size:14px}.bars{display:flex;flex-direction:column;gap:7px}.bars>div{display:grid;grid-template-columns:60px 1fr 25px;gap:8px;align-items:center;font-size:10px}.bars span{color:var(--muted);text-align:right}.bars i{height:11px;background:#eef2f6;border-radius:2px;overflow:hidden}.bars i b{display:block;height:100%;border-radius:2px}.bars em{font-style:normal;font-weight:700}.donut-chart{display:flex;flex-direction:column;align-items:center}.donut-chart h3{align-self:flex-start}.donut{width:136px;height:136px;border-radius:50%;display:grid;place-items:center;position:relative}.donut::after{content:'';position:absolute;width:82px;height:82px;border-radius:50%;background:white}.donut>span{z-index:1;display:flex;flex-direction:column;align-items:center;font-size:20px;font-weight:800}.donut small{font-size:9px;color:var(--muted);font-weight:400}.legend{margin-top:14px;display:flex;gap:9px;flex-wrap:wrap;justify-content:center}.legend span{display:flex;align-items:center;gap:4px;color:var(--muted);font-size:9px}.legend i{width:7px;height:7px;border-radius:2px}.task-panel{margin-top:12px;overflow:hidden}.task-header{padding:17px;display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--line)}.task-header h2{margin:0;font-size:18px}.task-header p{margin:4px 0 0;color:var(--muted);font-size:11px}.task-actions{display:flex;gap:7px}.filter-grid{padding:12px;display:grid;grid-template-columns:repeat(7,minmax(120px,1fr)) auto auto;gap:7px;background:#f8fafc;border-bottom:1px solid var(--line)}.filter-count{align-self:center;color:var(--muted);font-size:10px;white-space:nowrap}.stage-label{display:flex;align-items:center;gap:7px}.stage-label i{width:4px;height:20px;border-radius:2px}.status-button{width:76px;height:25px;padding:0 7px;border:1px solid;border-radius:4px;background:white;font-size:11px;cursor:pointer}.status-button:hover{filter:brightness(.96)}.status-待创建{color:#64748b;border-color:#cbd5e1;background:#f8fafc}.status-进行中{color:#b45309;border-color:#f3c277;background:#fffbeb}.status-已完成{color:#15803d;border-color:#86d49a;background:#f0fdf4}.pagination{padding:15px;display:flex;justify-content:flex-end}.source-note{text-align:center;color:var(--muted);font-size:10px}.source-note a{color:var(--brand)}.detail-list label{display:block;margin-top:18px;color:var(--muted);font-size:11px}.detail-list p{margin:5px 0;padding-bottom:13px;border-bottom:1px solid var(--line);line-height:1.7}.detail-list code{padding:3px 6px;background:#eef2f7;border-radius:3px;color:var(--brand)}
@media(max-width:1150px){.charts-grid{grid-template-columns:1fr 1fr}.stage-chart{grid-column:1/3}.filter-grid{grid-template-columns:repeat(4,1fr)}}
@media(max-width:760px){.workbench-container{width:min(100% - 18px,1500px)}.stat-grid{grid-template-columns:repeat(3,1fr)}.team-grid{grid-template-columns:1fr}.charts-grid{grid-template-columns:1fr}.stage-chart{grid-column:auto}.filter-grid{grid-template-columns:1fr 1fr}.task-header{align-items:flex-start;flex-direction:column;gap:12px}.task-panel :deep(.el-table){font-size:11px}.pagination{overflow-x:auto;justify-content:flex-start}}
@media(max-width:480px){.stat-grid{grid-template-columns:1fr 1fr}.filter-grid{grid-template-columns:1fr}.task-actions{width:100%}.task-actions .el-button{flex:1}.pagination :deep(.el-pagination__sizes),.pagination :deep(.el-pagination__total){display:none}}
</style>
