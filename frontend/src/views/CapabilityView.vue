<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CapabilityDetailFrame from '../components/CapabilityDetailFrame.vue'
import { useCapability } from '../composables/useCapability'
import { toolExperienceAccess } from '../tools/runtime/availability'

const props = defineProps<{ slug?: string }>()
const route = useRoute()
const router = useRouter()
const slug = computed(() => props.slug || String(route.params.slug))
const { selected, manifest, loading, error, load } = useCapability(slug)
const localPreview = computed(() => selected.value?.source === 'bundled')
const experience = computed(() => toolExperienceAccess(selected.value))
const runnable = computed(() => Boolean(selected.value) && !experience.value.reason)
const backLabel = computed(() => manifest.value?.kind === 'tool' ? '返回 AI 工具库' : '返回 Skill 能力库')

function run() { if (runnable.value && manifest.value) router.push(`/tools/${manifest.value.identity.slug}`) }
function back() { router.push(manifest.value?.kind === 'tool' ? '/tools' : '/skills') }
</script>

<template>
  <section class="capability-page"><div v-loading="loading" class="container wide">
    <CapabilityDetailFrame v-if="manifest" :manifest="manifest" :preview="localPreview" :selection="selected" :runnable="runnable" :experience-only="experience.serverPreview" :back-label="backLabel" @run="run" @back="back" />
    <el-empty v-else-if="error" :description="error"><el-button @click="load">重新读取</el-button></el-empty>
  </div></section>
</template>

<style scoped>.capability-page{min-height:70vh;padding:34px 0 64px;background:#f2f4f7}.wide{width:min(1450px,calc(100% - 30px))}</style>
