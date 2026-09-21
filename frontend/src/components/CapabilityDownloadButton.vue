<script lang="ts">
import { ref } from 'vue'
import type { LocalCapabilityPackage } from '../capabilityDownload'
const packages = ref<LocalCapabilityPackage[]>([])
let indexRequest: Promise<void> | undefined
async function loadPackages() {
  if (!indexRequest) indexRequest = (async () => {
    const response = await fetch(`${import.meta.env.BASE_URL}__local-capability-packages/index.json`, { cache: 'no-store' })
    if (!response.ok) throw new Error('本地能力包尚未准备好')
    const data = await response.json()
    if (!Array.isArray(data)) throw new Error('能力包索引无效')
    packages.value = data
  })().catch(() => { indexRequest = undefined })
  await indexRequest
}
</script>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import type { CapabilitySelection } from '../capabilityAccess'
import { capabilityErrorMessage } from '../capabilityAccess'
import { capabilityDownloadAccess } from '../capabilityDownload'
import { portalApi } from '../api/portal'

const props = defineProps<{ selected: CapabilitySelection; primary?: boolean; compact?: boolean }>()
const loading = ref(false)
const bundledDownloads = import.meta.env.DEV || import.meta.env.VITE_BUNDLED_DOWNLOADS === 'true'
const access = computed(() => capabilityDownloadAccess(props.selected, packages.value, bundledDownloads))
const note = computed(() => access.value.reason || (props.selected.source === 'bundled'
  ? props.selected.manifest.kind === 'tool' ? '包含工具源码与接入资料，需配合完整项目使用。' : '完整能力包，使用前请阅读中文手册。'
  : '下载当前已发布版本的交付包。'))
onMounted(() => { if (bundledDownloads && props.selected.source === 'bundled') void loadPackages() })
async function download() {
  if (loading.value || access.value.reason) return
  loading.value = true
  try {
    let blob: Blob
    let name: string
    if (props.selected.source === 'bundled') {
      const entry = access.value.local!
      const response = await fetch(`${import.meta.env.BASE_URL}__local-capability-packages/content/${entry.slug}`, { cache: 'no-store' })
      if (!response.ok || !response.headers.get('content-type')?.includes('application/octet-stream')) throw new Error('本地能力包不可用，请重新生成交付包后刷新页面')
      blob = await response.blob(); name = entry.file
      if (blob.size !== entry.bytes) throw new Error('能力包不完整，请重试')
    } else {
      const response = await portalApi.downloadCapability(props.selected.record!.slug)
      blob = response.data
      const encoded = String(response.headers['content-disposition'] || '').match(/filename\*=UTF-8''([^;]+)/i)?.[1]
      name = `${props.selected.manifest.identity.slug}-${props.selected.manifest.identity.version}.zip`
      if (encoded) { try { name = decodeURIComponent(encoded) } catch { /* 使用版本文件名 */ } }
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = url; link.download = name.replace(/[\\/]/g, '-')
    link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1500)
  } catch (cause) { ElMessage.error(capabilityErrorMessage(cause)) }
  finally { loading.value = false }
}
</script>

<template>
  <div v-if="!access.reason" class="package-download" @click.stop>
    <el-button :type="primary ? 'primary' : 'default'" :icon="Download" :disabled="!!access.reason" :loading="loading" @click="download">{{ access.label }}</el-button>
    <small v-if="!compact">{{ note }}</small>
  </div>
</template>
<style scoped>
.package-download{display:flex;flex-direction:column;align-items:flex-start;gap:8px;max-width:350px}.package-download small{font-size:14px;line-height:1.65;color:#6b5c42}.package-download .el-button{margin:0}
</style>
