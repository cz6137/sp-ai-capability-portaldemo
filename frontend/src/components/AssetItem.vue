<script setup lang="ts">
import { View, Download, Link, CopyDocument } from '@element-plus/icons-vue'
import { assetImaUrl, assetImaHomeUrl, assetImaLocation } from '../assetLinks'
import type { Asset } from '../types'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'

const props = withDefaults(defineProps<{ asset: Asset; showType?: boolean }>(), { showType: true })
async function copyFileName() {
  try { await navigator.clipboard.writeText(props.asset.name); ElMessage.success('已复制文件名，可在 IMA 中搜索') }
  catch { ElMessage.warning('当前浏览器未允许复制，请手动选择文件名复制') }
}

function fileIcon(name: string) {
  if (/\.(xlsx?|csv)$/i.test(name)) return 'XLS'
  if (/\.(pptx?)$/i.test(name)) return 'PPT'
  if (/\.(png|jpe?g|gif)$/i.test(name)) return 'IMG'
  if (/\.pdf$/i.test(name)) return 'PDF'
  return 'DOC'
}
async function openFile(asset: Asset, action: 'preview' | 'download') {
  if (!asset.file_id) return
  try {
    const response = await http.get(`/files/${asset.file_id}/${action}`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    if (action === 'preview') window.open(url, '_blank', 'noopener')
    else { const link = document.createElement('a'); link.href = url; link.download = asset.name; link.click() }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch { ElMessage.error(action === 'preview' ? '文件预览失败' : '文件下载失败') }
}

</script>

<template>
  <article class="asset-item" :class="'material-' + asset.type" :data-media-id="asset.media_id">
    <div class="file-badge">{{ fileIcon(asset.name) }}</div>
    <div class="asset-copy">
      <h4><a v-if="assetImaUrl(asset)" class="asset-name" :href="assetImaHomeUrl(asset)" target="_blank" rel="noopener noreferrer">{{ asset.name }}</a><span v-else>{{ asset.name }}</span></h4>
      <p>{{ assetImaUrl(asset) ? assetImaLocation(asset) : asset.sub_name }}</p>
    </div>
    <div class="asset-actions">
      <span v-if="showType" class="asset-type" :class="`type-${asset.type.toLowerCase()}`">{{ asset.type === 'T' ? 'SD 模板' : asset.type === 'C' ? '卓越案例' : 'AI 工具' }}</span>
      <template v-if="asset.file_id">
        <el-button circle :icon="View" title="在线预览" @click="openFile(asset, 'preview')" />
        <el-button circle :icon="Download" title="下载" @click="openFile(asset, 'download')" />
      </template>
      <el-button v-if="assetImaUrl(asset)" text :icon="CopyDocument" @click="copyFileName">复制文件名</el-button>
      <el-button v-if="assetImaUrl(asset)" tag="a" :href="assetImaHomeUrl(asset)" target="_blank" rel="noopener noreferrer" :icon="Link" title="打开 IMA 知识库">打开 IMA</el-button>
    </div>
  </article>
</template>

<style scoped>
.asset-item{display:grid;grid-template-columns:36px minmax(0,1fr);gap:8px 12px;align-items:start;padding:14px 16px;background:#f5f8fc;border:1px solid #edf0f5;border-left:3px solid #3973c9;box-shadow:none}.asset-item.material-C{border-left-color:#32816f;background:#f5f9f8}.asset-item.material-A{border-left-color:#bf712d}.asset-item:hover{box-shadow:none;border-top-color:#d7dfe9;border-right-color:#d7dfe9;border-bottom-color:#d7dfe9}.file-badge{width:36px;height:36px;font-size:10px;background:white;border:1px solid #e1e7ef}.asset-copy{grid-column:2;min-width:0}.asset-copy h4{font-size:16px;font-weight:600;line-height:1.7;margin:0 0 5px;user-select:text}.asset-copy p{font-size:14px;line-height:1.6;overflow-wrap:anywhere}.asset-actions{grid-column:2;display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:6px;margin:0}.asset-actions :deep(.el-button){font-size:14px}.asset-type{margin-right:auto;font-size:12px}.asset-name{color:inherit;overflow-wrap:anywhere}.asset-name:hover{color:var(--brand);text-decoration:underline}.asset-name:focus-visible{outline:2px solid var(--brand);outline-offset:3px}@media(max-width:480px){.asset-item{padding:12px;gap:8px}.asset-actions{grid-column:1 / -1}.asset-copy h4{font-size:15px}.asset-copy p{font-size:13px}}
</style>
