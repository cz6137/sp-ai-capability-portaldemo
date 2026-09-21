<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CapabilityGovernancePanel from '../components/CapabilityGovernancePanel.vue'
import { portalApi, type ManagedCapability } from '../api/portal'
import { capabilityOperationError } from '../capabilityGovernance'
const records = ref<ManagedCapability[]>([])
const selected = ref<ManagedCapability>()
const loading = ref(false)
const failure = ref('')
async function load() { loading.value = true; failure.value = ''; try { records.value = await portalApi.myCapabilities() as unknown as ManagedCapability[] } catch (e) { failure.value = capabilityOperationError(e); records.value = [] } finally { loading.value = false } }
function open(record: ManagedCapability) { selected.value = record }
onMounted(load)
</script>
<template>
  <section class="my-capabilities">
    <header class="page-title"><div><span>MY · CAPABILITIES</span><h1>我的能力提交</h1><p>查看本人提交的能力、版本和审核意见。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
    <CapabilityGovernancePanel v-if="selected" :asset-id="selected.id" :asset-name="selected.name" employee @back="selected = undefined" @changed="load" />
    <el-table v-else v-loading="loading" :data="records" empty-text="暂无提交"><el-table-column prop="name" label="能力名称"/><el-table-column prop="slug" label="标识"/><el-table-column prop="version" label="版本"/><el-table-column prop="status" label="状态"/><el-table-column prop="updatedAt" label="更新时间"/><el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看版本</el-button></template></el-table-column></el-table>
  </section>
</template>
<style scoped>.my-capabilities{max-width:1280px;margin:0 auto;padding:36px 24px}.page-title{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:24px}.page-title span{color:#ad2d2d;font-weight:800;letter-spacing:.1em}.page-title h1{margin:8px 0}.page-title p{color:#627086}</style>
