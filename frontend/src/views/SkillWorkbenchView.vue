<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Download, Edit, List, Plus, Search, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '../components/PageHero.vue'
import { portalApi } from '../api/portal'
import { STAGE_COLORS } from '../constants'
import { STAGE_NAMES } from '../data'
import type { ManagedSkill, SkillForm, SkillRecord } from '../types'

const router = useRouter()
const rows = ref<ManagedSkill[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const keyword = ref('')
const status = ref('all')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()

const emptyForm = (): SkillForm => ({ slug: '', name: '', description: '', sourceType: 'INTERNAL', caseText: '', usageGuide: '', stageIds: [], versionName: '1.0.0', changeNote: '' })
const form = reactive<SkillForm>(emptyForm())
const filtered = computed(() => rows.value.filter(row =>
  (status.value === 'all' || row.skill.status === status.value) &&
  (!keyword.value.trim() || `${row.skill.name} ${row.skill.slug}`.toLowerCase().includes(keyword.value.trim().toLowerCase()))
))
const stats = computed(() => ({
  total: rows.value.length,
  published: rows.value.filter(row => row.skill.status === 'PUBLISHED').length,
  reviewing: rows.value.filter(row => ['IN_REVIEW','APPROVED'].includes(row.skill.status)).length,
}))

async function load() {
  loading.value = true
  try { rows.value = await portalApi.managedSkills() }
  catch { ElMessage.error('Skill 工作台数据读取失败') }
  finally { loading.value = false }
}
function openCreate() { Object.assign(form, emptyForm()); selectedFile.value = null; dialogOpen.value = true }
function openEdit(skill: SkillRecord) {
  Object.assign(form, { id: skill.id, slug: skill.slug, name: skill.name, description: skill.description || '', sourceType: skill.sourceType, caseText: skill.caseText || '', usageGuide: skill.usageGuide || '', stageIds: [...skill.stageIds], versionName: '', changeNote: '' })
  selectedFile.value = null; dialogOpen.value = true
}
function pickFile(event: Event) { selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null }
async function save() {
  if (!form.name.trim()) { ElMessage.warning('请填写 Skill 名称'); return }
  if (!form.id && !selectedFile.value) { ElMessage.warning('请选择要导入的 Skill 包'); return }
  saving.value = true
  try {
    if (selectedFile.value) await portalApi.importSkill(selectedFile.value, { ...form, stageIds: [...form.stageIds] })
    else await portalApi.saveSkill({ ...form, stageIds: [...form.stageIds] })
    ElMessage.success(selectedFile.value ? 'Skill 包已导入' : 'Skill 信息已保存')
    dialogOpen.value = false; await load()
  } catch { ElMessage.error('Skill 保存失败，请检查名称、版本或文件') }
  finally { saving.value = false }
}
async function exportSkill(skill: SkillRecord) {
  try {
    const response = await portalApi.downloadSkill(skill.id)
    const url = URL.createObjectURL(response.data); const link = document.createElement('a'); link.href = url; link.download = `${skill.slug}.zip`; link.click(); URL.revokeObjectURL(url)
    skill.downloadCount++
  } catch { ElMessage.warning('该 Skill 暂无可导出的已发布包') }
}
async function archive(skill: SkillRecord) {
  try {
    await ElMessageBox.confirm(`归档“${skill.name}”后将不再在工具库展示。`, '归档 Skill', { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' })
    await portalApi.archiveSkill(skill.id); ElMessage.success('Skill 已归档'); await load()
  } catch { /* cancelled or handled by request interceptor */ }
}
function statusType(value: string) { return value === 'PUBLISHED' ? 'success' : value === 'IN_REVIEW' ? 'warning' : value === 'ARCHIVED' ? 'info' : '' }
onMounted(load)
</script>

<template>
  <PageHero eyebrow="SKILL OPERATIONS" title="Skill 工作台" description="集中管理 Skill 元数据、版本包、适用阶段与发布状态；前台工具库只展示已发布能力。">
    <div class="hero-actions"><el-button type="primary" :icon="Plus" @click="openCreate">导入 Skill</el-button><el-button :icon="List" @click="router.push('/tasks')">交付任务看板</el-button></div>
  </PageHero>
  <section class="workbench-section"><div class="container wide">
    <div class="stats"><div><span>全部 Skill</span><b>{{ stats.total }}</b></div><div><span>已发布</span><b>{{ stats.published }}</b></div><div><span>审核中</span><b>{{ stats.reviewing }}</b></div></div>
    <section class="manage-panel surface">
      <header><div><h2>Skill 总览</h2><p>上传新包会生成一个版本；编辑元数据不会覆盖既有版本文件。</p></div><el-button type="primary" :icon="UploadFilled" @click="openCreate">上传 / 导入</el-button></header>
      <div class="filters"><el-segmented v-model="status" :options="[{label:'全部',value:'all'},{label:'草稿',value:'DRAFT'},{label:'审核中',value:'IN_REVIEW'},{label:'已发布',value:'PUBLISHED'},{label:'已归档',value:'ARCHIVED'}]"/><el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索名称或标识"/><span>{{ filtered.length }} 条</span></div>
      <el-table v-loading="loading" :data="filtered" stripe table-layout="fixed">
        <el-table-column label="Skill" min-width="250"><template #default="{row}"><div class="skill-cell"><b>{{ row.skill.name }}</b><code>{{ row.skill.slug }}</code></div></template></el-table-column>
        <el-table-column label="适用阶段" min-width="230"><template #default="{row}"><div class="table-stages"><span v-for="stage in row.skill.stageIds" :key="stage" :style="{ '--stage-color': STAGE_COLORS[stage] }">{{ stage }} {{ STAGE_NAMES[stage] }}</span><small v-if="!row.skill.stageIds.length">通用</small></div></template></el-table-column>
        <el-table-column label="版本" width="105"><template #default="{row}">{{ row.latestVersion?.versionName || '-' }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag size="small" :type="statusType(row.skill.status)">{{ row.skill.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><el-button circle text :icon="Edit" title="编辑" @click="openEdit(row.skill)"/><el-button circle text :icon="Download" title="导出 Skill 包" @click="exportSkill(row.skill)"/><el-button circle text type="danger" :icon="Delete" title="归档" @click="archive(row.skill)"/></template></el-table-column>
      </el-table>
    </section>
  </div></section>

  <el-dialog v-model="dialogOpen" :title="form.id ? '编辑 Skill' : '导入 Skill'" width="min(720px,94vw)" destroy-on-close>
    <el-form label-position="top" class="skill-form">
      <div class="two"><el-form-item label="Skill 名称" required><el-input v-model="form.name" placeholder="例如：项目周报生成器"/></el-form-item><el-form-item label="英文标识"><el-input v-model="form.slug" placeholder="delivery-weekly-report"/></el-form-item></div>
      <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="说明能力、输入和主要产出"/></el-form-item>
      <el-form-item label="适用项目阶段"><el-checkbox-group v-model="form.stageIds" class="stage-checks"><el-checkbox v-for="(name,index) in STAGE_NAMES" :key="name" :value="index">{{ index }} {{ name }}</el-checkbox></el-checkbox-group></el-form-item>
      <div class="two"><el-form-item label="来源"><el-radio-group v-model="form.sourceType"><el-radio-button value="INTERNAL">内部自研</el-radio-button><el-radio-button value="EXTERNAL">外部推荐</el-radio-button></el-radio-group></el-form-item><el-form-item label="版本号"><el-input v-model="form.versionName" placeholder="1.0.0"/></el-form-item></div>
      <el-form-item label="应用案例"><el-input v-model="form.caseText" type="textarea" :rows="3" placeholder="填写实际使用场景、项目或效果"/></el-form-item>
      <el-form-item label="使用指南"><el-input v-model="form.usageGuide" type="textarea" :rows="4" placeholder="填写准备材料、执行步骤和注意事项"/></el-form-item>
      <el-form-item label="Skill 包"><div class="file-picker"><input ref="fileInput" type="file" accept=".zip,.jar,.tar,.gz" @change="pickFile"/><el-button :icon="UploadFilled" @click="fileInput?.click()">选择文件</el-button><span>{{ selectedFile?.name || (form.id ? '未选择新包，仅保存元数据' : '请选择 ZIP 等 Skill 包') }}</span></div></el-form-item>
      <el-form-item v-if="selectedFile" label="版本说明"><el-input v-model="form.changeNote" placeholder="本版本新增或修复内容"/></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ selectedFile ? '导入并保存' : '保存信息' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.hero-actions{display:flex;gap:9px;margin-top:20px}.workbench-section{padding:28px 0 50px;background:#f4f6f9}.wide{width:min(1450px,calc(100% - 30px))}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:12px}.stats>div{padding:16px 18px;background:white;border:1px solid var(--line);border-top:3px solid #64748b;display:flex;flex-direction:column;gap:4px}.stats span{color:var(--muted);font-size:11px}.stats b{font-size:25px}.manage-panel{overflow:hidden}.manage-panel>header{padding:18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line)}.manage-panel h2{margin:0;font-size:18px}.manage-panel header p{margin:4px 0 0;color:var(--muted);font-size:11px}.filters{padding:12px;display:grid;grid-template-columns:auto minmax(220px,380px) auto;align-items:center;gap:12px;background:#f8fafc;border-bottom:1px solid var(--line)}.filters>span{color:var(--muted);font-size:11px}.skill-cell{display:flex;flex-direction:column;gap:4px}.skill-cell code{color:var(--muted);font-size:10px}.table-stages{display:flex;flex-wrap:wrap;gap:4px}.table-stages span{padding:3px 6px;border-left:3px solid var(--stage-color);background:#f1f4f7;font-size:10px}.table-stages small{color:var(--muted)}.skill-form .two{display:grid;grid-template-columns:1fr 1fr;gap:14px}.stage-checks{display:grid;grid-template-columns:repeat(3,1fr);width:100%}.file-picker{width:100%;padding:12px;border:1px dashed #cbd5e1;background:#f8fafc;display:flex;align-items:center;gap:10px}.file-picker input{display:none}.file-picker span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted);font-size:11px}
.stats{grid-template-columns:repeat(3,1fr)}
@media(max-width:760px){.wide{width:calc(100% - 18px)}.stats{grid-template-columns:1fr 1fr}.filters{grid-template-columns:1fr}.skill-form .two{grid-template-columns:1fr}.stage-checks{grid-template-columns:1fr 1fr}.manage-panel>header{align-items:flex-start;gap:12px}.hero-actions{flex-wrap:wrap}}
</style>
