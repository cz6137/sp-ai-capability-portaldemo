<script setup lang="ts">
import { baselineState, hydratePortalData } from '../data'
import { IMA_URL } from '../constants'
async function retry() { try { await hydratePortalData() } catch { /* 错误显示在数据来源提示中 */ } }
</script>
<template>
  <div class="baseline-source">
    <el-alert v-if="baselineState.error" type="error" :closable="false" show-icon :title="baselineState.error">
      <el-button text :loading="baselineState.loading" @click="retry">重新读取基线目录</el-button>
    </el-alert>
    <a :href="IMA_URL" target="_blank" rel="noopener noreferrer">打开 IMA 知识库 ↗</a>
  </div>
</template>
<style scoped>
.baseline-source{display:flex;align-items:center;gap:20px;margin-bottom:24px}.baseline-source>.el-alert,.baseline-source>p{flex:1}.baseline-source :deep(.el-alert__description),.baseline-source p{font-size:16px;line-height:1.8}.baseline-source>a{flex-shrink:0;font-size:16px;font-weight:700;color:#ad2d2d}@media(max-width:720px){.baseline-source{align-items:flex-start;flex-direction:column}}
</style>
