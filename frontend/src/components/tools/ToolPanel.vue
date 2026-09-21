<script setup lang="ts">
defineProps<{ step: string; title: string; description?: string }>()
</script>

<template>
  <section class="surface tool-panel">
    <header class="tool-panel-heading">
      <div class="tool-panel-title"><span>{{ step }}</span><div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div></div>
      <div class="tool-panel-tools"><slot name="tools" /></div>
    </header>
    <slot />
    <footer v-if="$slots.footer" class="tool-panel-footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.tool-panel{padding:28px}
.tool-panel-heading{margin-bottom:20px;display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.tool-panel-title{min-width:0;display:flex;gap:13px}
.tool-panel-title>span{flex:0 0 36px;height:36px;display:grid;place-items:center;border-radius:50%;color:var(--brand);background:var(--brand-soft);font-size:12px;font-weight:800}
.tool-panel h2{margin:0;font-size:23px;line-height:1.35}
.tool-panel p{margin:6px 0 0;color:var(--muted);font-size:15px;line-height:1.7}
.tool-panel-tools{display:flex;flex:0 0 auto;align-items:center;gap:8px}
.tool-panel-footer{margin-top:20px;padding-top:18px;border-top:1px solid var(--line);display:flex;align-items:center;justify-content:flex-end;gap:10px}
@media(max-width:700px){.tool-panel{padding:22px 16px}.tool-panel-heading{flex-wrap:wrap}.tool-panel-tools{width:100%;justify-content:flex-end}.tool-panel-footer{align-items:stretch;flex-direction:column}.tool-panel-footer :deep(.el-button){width:100%;margin:0}}
</style>
