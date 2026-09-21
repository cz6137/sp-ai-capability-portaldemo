<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalApi, type CapabilityAction, type CapabilityAudit, type ManagedCapability } from '../api/portal'
import { availableCapabilityActions, capabilityActionLabels, capabilityOperationError, capabilityStatusLabel, isCapabilityContributor } from '../capabilityGovernance'
import { useAuthStore } from '../stores/auth'
import CapabilityDetailFrame from './CapabilityDetailFrame.vue'

const props = defineProps<{ assetId: string; assetName: string; employee?: boolean }>()
const emit = defineEmits<{ edit: [record: ManagedCapability, newVersion: boolean]; changed: []; back: [] }>()
const auth = useAuthStore()
const versions = ref<ManagedCapability[]>([])
const selected = ref<ManagedCapability>()
const audits = ref<CapabilityAudit[]>([])
const loading = ref(false)
const auditLoading = ref(false)
const busy = ref(false)
const failure = ref('')
const auditFailure = ref('')
const reason = ref('')
const showDetails = ref(false)
let auditRequest = 0
const contributor = computed(() => selected.value ? isCapabilityContributor(selected.value, audits.value, auth.user?.id) : false)
const actions = computed<CapabilityAction[]>(() => selected.value ? props.employee ? (selected.value.status === 'DRAFT' ? ['submit'] : []) : availableCapabilityActions(selected.value, contributor.value) : [])

async function choose(record: ManagedCapability) {
  const request = ++auditRequest
  selected.value = record; reason.value = ''; audits.value = []; auditFailure.value = ''; auditLoading.value = true; showDetails.value = false
  try { const result = await (props.employee ? portalApi.myCapabilityAudits : portalApi.capabilityAudits)(props.assetId, record.versionId); if (request === auditRequest) audits.value = result }
  catch (error) { if (request === auditRequest) auditFailure.value = capabilityOperationError(error) }
  finally { if (request === auditRequest) auditLoading.value = false }
}
async function load(versionId = selected.value?.versionId) {
  loading.value = true; failure.value = ''; selected.value = undefined; audits.value = []; ++auditRequest
  try {
    versions.value = await (props.employee ? portalApi.myCapabilityVersions : portalApi.capabilityVersions)(props.assetId)
    const first = versions.value.find(item => item.versionId === versionId) || versions.value[0]
    if (first) await choose(first)
  } catch (error) { versions.value = []; failure.value = capabilityOperationError(error) }
  finally { loading.value = false }
}
async function act(action: CapabilityAction) {
  if (!selected.value || busy.value || !reason.value.trim() || !actions.value.includes(action)) return
  busy.value = true; failure.value = ''
  const record = selected.value
  try {
    if (props.employee) await portalApi.submitMyCapability(record, reason.value.trim())
    else await portalApi.capabilityAction(record, action, reason.value.trim())
    ElMessage.success(`${capabilityActionLabels[action]}成功`); emit('changed'); await load(record.versionId)
  } catch (error) { failure.value = capabilityOperationError(error) }
  finally { busy.value = false }
}
async function download() {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    const response = await (props.employee ? portalApi.downloadMyCapabilityVersion : portalApi.downloadCapabilityVersion)(props.assetId, selected.value.versionId)
    const header = String(response.headers['content-disposition'] || '')
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)?.[1]
    if (!encoded) throw new Error('下载响应缺少文件名')
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a'); link.href = url; link.download = decodeURIComponent(encoded); link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) { failure.value = capabilityOperationError(error) }
  finally { busy.value = false }
}
function auditReason(log: CapabilityAudit) {
  try { return JSON.parse(log.afterData).reason || '未记录意见' } catch { return '历史记录格式无法识别' }
}
function actionLabel(action: string) { return action === 'CAPABILITY_IMPORT' ? '保存草稿' : capabilityActionLabels[action.replace('CAPABILITY_', '').toLowerCase() as CapabilityAction] || action }
onMounted(() => load())
</script>

<template>
  <section class="governance" v-loading="loading">
    <header><div><h2>{{ assetName }} · 版本与审核</h2><p>先保存草稿，再提交审核。新草稿不会替换当前发布版本；审核账号由后台记录。</p></div><div><el-button :disabled="busy || loading" @click="load()">刷新</el-button><el-button :disabled="busy" @click="emit('back')">返回资产清单</el-button></div></header>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
    <div class="version-layout" v-if="versions.length">
      <nav aria-label="能力历史版本"><button v-for="item in versions" :key="item.versionId" :disabled="busy || loading" :class="{ selected: item.versionId === selected?.versionId }" @click="choose(item)"><b>{{ item.version }}</b><span>{{ capabilityStatusLabel(item) }}</span></button></nav>
      <main v-if="selected">
        <h3>{{ selected.name }} {{ selected.version }}</h3>
        <p class="state">{{ capabilityStatusLabel(selected) }} · {{ selected.manifest.governance.changeNote }}</p>
        <p>创建账号：{{ selected.createdBy || '历史记录未提供' }}<br>更新时间：{{ selected.updatedAt }}<br>审核账号：{{ selected.manifest.governance.reviewer || '尚未审核' }}</p>
        <div class="version-tools"><el-button @click="showDetails = !showDetails">{{ showDetails ? '收起清单详情' : '查看清单详情' }}</el-button><el-button v-if="selected.status === 'DRAFT'" :disabled="busy" @click="emit('edit', selected, false)">编辑草稿</el-button><el-button :disabled="busy" @click="emit('edit', selected, true)">基于此版本新建</el-button><el-button :disabled="busy || !selected.packageFileId" @click="download">下载此版本交付包</el-button></div>
        <CapabilityDetailFrame v-if="showDetails" :manifest="selected.manifest" preview />
        <h3>治理操作</h3>
        <el-alert v-if="auditFailure" :title="auditFailure" type="error" :closable="false" />
        <p v-if="contributor && selected.status === 'IN_REVIEW' && !selected.reviewApproved">你参与了本版本的创建、修改或提交，请由另一位管理员审核。</p>
        <p v-if="selected.status === 'PUBLISHED'">已发布内容和交付包不可覆盖。修改时请新建版本；下架后保留历史记录，归档前须先下架。</p>
        <template v-if="actions.length"><label for="governance-reason">操作意见（必填，记录在审计中）</label><el-input id="governance-reason" v-model="reason" type="textarea" :rows="3" maxlength="1000" show-word-limit :disabled="busy" placeholder="填写审核结论、退回原因或发布／下架依据"/><div class="action-buttons"><el-button v-for="action in actions" :key="action" :type="['reject', 'unpublish', 'archive'].includes(action) ? 'default' : 'primary'" :disabled="busy || loading || auditLoading || !!auditFailure || !reason.trim()" @click="act(action)">{{ capabilityActionLabels[action] }}</el-button></div></template>
        <p v-else-if="selected.status === 'ARCHIVED'">已归档版本保留追溯，后续修改请创建新版本。</p>
        <h3>操作记录</h3>
        <el-table :data="audits" v-loading="auditLoading" :empty-text="auditFailure ? '记录读取失败' : '该版本尚无操作记录'"><el-table-column label="操作" min-width="120"><template #default="{row}">{{ actionLabel(row.action) }}</template></el-table-column><el-table-column prop="actorId" label="实际操作账号" min-width="160"/><el-table-column label="意见" min-width="230"><template #default="{row}">{{ auditReason(row) }}</template></el-table-column><el-table-column prop="createdAt" label="时间" min-width="210"/></el-table>
      </main>
    </div>
    <el-empty v-else-if="!loading && !failure" description="该能力还没有版本记录" />
  </section>
</template>

<style scoped>
.governance{background:#fff;border:1px solid #dce2ea}.governance>header{padding:24px;display:flex;justify-content:space-between;gap:24px;border-bottom:1px solid #dce2ea}.governance h2{font-size:24px;margin:0 0 10px}.governance p{font-size:16px;color:#59697f;line-height:1.8}.governance header p{margin:0}.version-layout{display:grid;grid-template-columns:205px minmax(0,1fr)}nav{padding:18px;background:#f4f6f9}nav button{display:flex;flex-direction:column;gap:8px;text-align:left;width:100%;padding:16px;margin-bottom:8px;border:1px solid #dce2ea;background:white;color:#172238;cursor:pointer}nav button.selected{border-left:4px solid #ad2d2d}nav b{font-size:18px}nav span{font-size:15px}main{padding:26px;min-width:0}h3{font-size:21px;margin:28px 0 16px}h3:first-child{margin-top:0}.state{color:#ad2d2d!important;font-weight:600}label{display:block;font-size:16px;margin-bottom:10px}.version-tools,.action-buttons{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.version-tools .el-button,.action-buttons .el-button{margin-left:0}:deep(.el-table),:deep(.el-textarea__inner){font-size:15px}@media(max-width:900px){.version-layout{display:block}.governance>header{flex-direction:column}nav{display:flex;gap:8px;overflow-x:auto}nav button{min-width:170px}main{padding:18px}}
</style>
