<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { capabilityToLegacyTool } from '../capabilityManifest'
import { useCapability } from '../composables/useCapability'
import { toolExperienceAccess } from '../tools/runtime/availability'
import { registeredServerJobRenderer } from '../tools/runtime/rendererRegistry'

const route = useRoute()
const router = useRouter()
const slug = computed(() => String(route.params.id))
const { selected, manifest, loading, error, load } = useCapability(slug)
const experience = computed(() => toolExperienceAccess(selected.value))
const unavailable = computed(() => experience.value.reason)
const tool = computed(() => manifest.value && !unavailable.value ? capabilityToLegacyTool(manifest.value) : undefined)
const DocumentTransformRenderer = defineAsyncComponent(() => import('../components/tools/DocumentTransformRenderer.vue'))
const ServerJobRenderer = computed(() => registeredServerJobRenderer(tool.value?.runtime.handler))
const rendererKey = computed(() => `${manifest.value?.identity.slug}:${manifest.value?.identity.version}`)
</script>

<template>
  <div v-loading="loading" class="runtime-page">
    <DocumentTransformRenderer v-if="tool?.runtime.renderer === 'document-transform'" :key="rendererKey" :tool="tool" />
    <component :is="ServerJobRenderer" v-else-if="tool && ServerJobRenderer" :key="rendererKey" :tool="tool" :preview="experience.serverPreview" />
    <section v-else-if="!loading" class="section"><div class="container surface unavailable"><h1>{{ error ? '暂时无法读取工具' : '工具暂未开放' }}</h1><p>{{ error || unavailable }}</p><div><el-button v-if="error" @click="load">重新读取</el-button><el-button v-if="manifest" @click="router.push(`/capabilities/${manifest.identity.slug}`)">查看能力说明</el-button><el-button type="primary" @click="router.push('/tools')">返回工具目录</el-button></div></div></section>
  </div>
</template>

<style scoped>.runtime-page{min-height:65vh}.preview-notice{padding-top:20px}.unavailable{padding:48px;text-align:center}.unavailable h1{margin:0 0 12px}.unavailable p{margin:0 0 24px;color:var(--muted);font-size:17px;line-height:1.8}.unavailable>div{display:flex;justify-content:center;gap:12px;flex-wrap:wrap}</style>
