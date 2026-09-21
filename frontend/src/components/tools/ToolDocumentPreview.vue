<script setup lang="ts">
import { ref, watch } from 'vue'
import { renderMarkdown } from '../../utils/documentConversion'

const props = withDefaults(defineProps<{ markdown: string; emptyText?: string }>(), { emptyText: '结果将在这里预览。' })
const previewElement = ref<HTMLElement>()
const previewHtml = ref('')
let version = 0

watch(() => props.markdown, async value => {
  const current = ++version
  const html = value.trim() ? await renderMarkdown(value) : `<p class="empty-preview">${props.emptyText}</p>`
  if (current === version) previewHtml.value = html
}, { immediate: true })

defineExpose({ getElement: () => previewElement.value })
</script>

<template>
  <div class="tool-document-preview">
    <header><b>文档预览</b><span>A4 阅读效果参考</span></header>
    <div class="tool-document-canvas"><article ref="previewElement" class="tool-document-page markdown-body" v-html="previewHtml"></article></div>
  </div>
</template>

<style scoped>
.tool-document-preview{min-width:0;display:flex;flex-direction:column;background:#e8ebf0}
.tool-document-preview>header{height:44px;padding:0 14px;display:flex;align-items:center;justify-content:space-between;background:#f5f7fa;border-bottom:1px solid var(--line)}
.tool-document-preview>header span{color:var(--muted);font-size:12px}
.tool-document-canvas{min-width:0;flex:1;overflow:auto}
.tool-document-page{width:min(100% - 36px,760px);min-height:560px;margin:18px auto;padding:48px 54px;background:#fff;box-shadow:0 3px 16px rgba(15,23,42,.12);overflow-wrap:anywhere}
.markdown-body{color:#202b3d;font-size:15px;line-height:1.85}.markdown-body :deep(h1){margin:0 0 24px;padding-bottom:13px;border-bottom:3px solid var(--brand);font-size:30px;line-height:1.35}.markdown-body :deep(h2){margin:28px 0 13px;padding-left:11px;border-left:4px solid var(--brand);font-size:22px}.markdown-body :deep(h3){margin:23px 0 10px;font-size:18px}.markdown-body :deep(p){margin:12px 0}.markdown-body :deep(blockquote){margin:18px 0;padding:10px 16px;color:#566376;background:#f5f7fa;border-left:4px solid #94a3b8}.markdown-body :deep(pre){padding:15px;overflow:auto;color:#e5edf7;background:#172033;border-radius:5px;font-size:13px}.markdown-body :deep(code){font-family:Consolas,monospace}.markdown-body :deep(:not(pre)>code){padding:2px 5px;color:#9a3412;background:#fff1e8;border-radius:3px}.markdown-body :deep(table){width:100%;margin:18px 0;border-collapse:collapse}.markdown-body :deep(th),.markdown-body :deep(td){padding:9px 11px;border:1px solid #cfd6df;text-align:left}.markdown-body :deep(th){background:#eef1f5}.markdown-body :deep(img){max-width:100%;height:auto}.markdown-body :deep(a){color:#1d4ed8;text-decoration:underline}.markdown-body :deep(.empty-preview){margin-top:180px;color:#94a3b8;text-align:center}
@media(max-width:700px){.tool-document-page{width:calc(100% - 20px);padding:30px 22px}}
</style>
