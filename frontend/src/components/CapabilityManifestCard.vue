<script setup lang="ts">
import { computed } from 'vue'
import { Box, Tools } from '@element-plus/icons-vue'
import type { CapabilityManifestBase } from '../capabilityManifest'

const props = defineProps<{ asset: CapabilityManifestBase }>()
const kindLabel = computed(() => props.asset.kind === 'skill' ? 'Skill 能力包' : '在线工具')
const maturityType = computed(() => props.asset.maturity === '已验证' ? 'success' : 'warning')
</script>

<template>
  <section class="capability-manifest-card surface">
    <header>
      <span><el-icon><component :is="asset.kind === 'skill' ? Box : Tools" /></el-icon>{{ kindLabel }}</span>
      <el-tag size="small" :type="maturityType">{{ asset.maturity }}</el-tag>
    </header>
    <h3>{{ asset.name }}</h3>
    <dl>
      <div><dt>当前版本</dt><dd>v{{ asset.version }}</dd></div>
      <div><dt>维护单位</dt><dd>{{ asset.maintainer }}</dd></div>
      <div><dt>更新时间</dt><dd>{{ asset.updatedAt }}</dd></div>
      <div><dt>交付方式</dt><dd>{{ asset.deployment.modes.join(' / ') }}</dd></div>
    </dl>
    <slot name="action" />
    <p>{{ asset.deployment.guide }}</p>
    <details class="technical-info">
      <summary>技术信息</summary>
      <dl>
        <div><dt>标识</dt><dd><code>{{ asset.slug }}</code></dd></div>
        <div><dt>Schema</dt><dd>{{ asset.schemaVersion }}</dd></div>
      </dl>
    </details>
  </section>
</template>

<style scoped>
.capability-manifest-card{padding:20px;border:1px solid var(--line);border-radius:4px;background:#fff}.capability-manifest-card header{display:flex;align-items:center;justify-content:space-between;gap:8px}.capability-manifest-card header>span{display:flex;align-items:center;gap:6px;color:#4d5a6d;font-size:13px;font-weight:700}.capability-manifest-card h3{margin:16px 0 6px;font-size:18px;line-height:1.5}.capability-manifest-card code{color:#6b7280;font-size:12px}.capability-manifest-card dl{margin:16px 0}.capability-manifest-card dl div{padding:10px 0;display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid var(--line);font-size:13px;line-height:1.55}.capability-manifest-card dt{color:#6b7280}.capability-manifest-card dd{margin:0;text-align:right;font-weight:650;overflow-wrap:anywhere}.capability-manifest-card :deep(.el-button){width:100%}.capability-manifest-card>p{margin:12px 0 0;color:#6b7280;font-size:12px;line-height:1.7;text-align:left}.technical-info{margin-top:14px;border-top:1px solid var(--line)}.technical-info summary{padding-top:14px;color:#6f7b8c;font-size:12px;cursor:pointer}.technical-info dl{margin:8px 0 0}
</style>
