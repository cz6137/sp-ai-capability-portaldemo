<script setup lang="ts">
import { computed, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  file?: File
  accept: string
  maxMb: number
  title?: string
  emptyText?: string
  compact?: boolean
  disabled?: boolean
}>(), { title: '选择或拖入文件', emptyText: '', compact: false, disabled: false })
const emit = defineEmits<{ select: [file: File] }>()
const input = ref<HTMLInputElement>()
const dragging = ref(false)
const helper = computed(() => props.emptyText || `支持 ${props.accept}，单个文件不超过 ${props.maxMb} MB`)

function choose() { if (!props.disabled) input.value?.click() }
function changed(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) emit('select', file)
  if (input.value) input.value.value = ''
}
function dropped(event: DragEvent) {
  dragging.value = false
  if (props.disabled) return
  const file = event.dataTransfer?.files?.[0]
  if (file) emit('select', file)
}
function formatSize(size: number) {
  return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.ceil(size / 1024))} KB`
}
</script>

<template>
  <input ref="input" class="tool-file-input" type="file" :accept="accept" :disabled="disabled" @change="changed">
  <button :class="['tool-drop-zone', { dragging, compact, disabled }]" type="button" :disabled="disabled" @click="choose" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="dropped">
    <el-icon><UploadFilled /></el-icon>
    <div><b>{{ file?.name || title }}</b><span v-if="file">{{ formatSize(file.size) }} · 已读取</span><span v-else>{{ helper }}</span></div>
    <em>{{ file ? '重新选择' : '选择文件' }}</em>
  </button>
</template>

<style scoped>
.tool-file-input{display:none}
.tool-drop-zone{width:100%;min-height:170px;padding:26px;display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:20px;border:2px dashed #bbc5d2;border-radius:8px;background:linear-gradient(180deg,#fbfcfe,#f5f7fa);color:inherit;text-align:left;cursor:pointer}
.tool-drop-zone:hover,.tool-drop-zone.dragging{border-color:var(--brand);background:#fffafa}
.tool-drop-zone.compact{min-height:92px;padding:16px 18px;border-radius:7px}
.tool-drop-zone.disabled{opacity:.6;cursor:not-allowed}
.tool-drop-zone>.el-icon{color:var(--brand);font-size:40px}
.tool-drop-zone.compact>.el-icon{font-size:28px}
.tool-drop-zone div{min-width:0;display:flex;flex-direction:column;gap:7px}
.tool-drop-zone b{font-size:17px;overflow-wrap:anywhere}
.tool-drop-zone span{color:var(--muted);font-size:13px;line-height:1.55}
.tool-drop-zone em{padding:9px 15px;border:1px solid #dcb4b4;border-radius:5px;color:var(--brand);background:#fff;font-size:13px;font-style:normal;white-space:nowrap}
@media(max-width:620px){.tool-drop-zone{grid-template-columns:1fr}.tool-drop-zone em{text-align:center}.tool-drop-zone>.el-icon{display:none}}
</style>
