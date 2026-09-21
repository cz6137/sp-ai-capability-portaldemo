<script setup lang="ts">
import { computed } from 'vue'
import CapabilityDownloadButton from './CapabilityDownloadButton.vue'
import { useCapability } from '../composables/useCapability'

const { selected, loading, error, load } = useCapability(computed(() => 'platform-skill-adapter'))
</script>

<template>
  <section class="intake-help" aria-labelledby="intake-help-title">
    <div class="help-copy">
      <h2 id="intake-help-title">外部能力需要适配？</h2>
      <p>使用「适应平台 Skill」整理清单和交付包。</p>
    </div>
    <div class="help-actions">
      <RouterLink to="/admin/skill-adapter" target="_blank" rel="noopener noreferrer">使用说明</RouterLink>
      <CapabilityDownloadButton v-if="selected" :selected="selected" primary compact />
      <span v-else-if="loading" role="status">正在读取适配 Skill…</span>
      <template v-else><p role="status">{{ error || '暂未提供可下载的适配 Skill' }}</p><el-button @click="load">重新读取</el-button></template>
    </div>
  </section>
</template>

<style scoped>
.intake-help{box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;gap:24px;width:100%;margin:0 0 18px;padding:16px 22px;border:1px solid #e4d8d8;border-left:4px solid #a32d2d;border-radius:6px;background:#fffafa}.help-copy{display:flex;align-items:baseline;gap:18px;min-width:0}.help-copy h2{margin:0;font-size:18px;color:#172238;white-space:nowrap}.help-copy p{margin:0;color:#627086;font-size:15px}.help-actions{display:flex;align-items:center;justify-content:flex-end;gap:16px;flex-shrink:0}.help-actions>a{font-size:15px;font-weight:600;color:#a32d2d}.help-actions>a:hover{text-decoration:underline}.help-actions>p,.help-actions>span{margin:0;font-size:14px;color:#687386}@media(max-width:900px){.intake-help,.help-copy{align-items:flex-start;flex-direction:column}.help-actions{justify-content:flex-start}}@media(max-width:600px){.intake-help{padding:16px}.help-copy h2{font-size:17px}}
</style>
