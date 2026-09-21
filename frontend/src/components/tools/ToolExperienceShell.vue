<script setup lang="ts">
import { CircleCheck, Lock, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ToolDefinition } from '../../capabilityManifest'
import PageHero from '../PageHero.vue'

const props = withDefaults(defineProps<{
  tool: ToolDefinition
  eyebrow?: string
  bannerTone?: 'safe' | 'ready' | 'warning' | 'blocked'
  bannerTitle?: string
  bannerText: string
  checking?: boolean
}>(), {
  eyebrow: 'TOOL EXPERIENCE',
  bannerTone: 'safe',
  bannerTitle: '',
  checking: false,
})

const emit = defineEmits<{ refresh: [] }>()
const router = useRouter()
</script>

<template>
  <PageHero :eyebrow="props.eyebrow" :title="props.tool.name" :description="props.tool.tagline">
    <button class="tool-back" type="button" @click="router.push('/tools')">← 返回 AI 工具库</button>
  </PageHero>

  <section :class="['tool-service-banner', props.bannerTone]">
    <div class="container">
      <el-icon>
        <Lock v-if="props.bannerTone === 'safe'" />
        <CircleCheck v-else-if="props.bannerTone === 'ready'" />
        <WarningFilled v-else />
      </el-icon>
      <p><b v-if="props.bannerTitle">{{ props.bannerTitle }}</b>{{ props.bannerText }}</p>
      <slot name="banner-action">
        <el-button v-if="props.bannerTone !== 'safe'" text :icon="Refresh" :loading="props.checking" @click="emit('refresh')">重新检查</el-button>
      </slot>
    </div>
  </section>

  <section class="section">
    <div class="container tool-experience-layout">
      <main class="tool-workspace"><slot /></main>
      <aside class="tool-aside"><slot name="aside" /></aside>
    </div>
  </section>
</template>

<style scoped>
.tool-back{min-height:44px;margin-top:22px;padding:0 18px;display:inline-flex;align-items:center;justify-content:center;border:1px solid rgba(255,255,255,.72);border-radius:6px;color:#fff;background:rgba(255,255,255,.12);box-shadow:0 4px 14px rgba(0,0,0,.16);font-size:15px;font-weight:700;cursor:pointer;transition:background-color .18s ease,color .18s ease,border-color .18s ease,transform .18s ease,box-shadow .18s ease}
.tool-back:hover{color:#172033;background:#fff;border-color:#fff;box-shadow:0 7px 18px rgba(0,0,0,.24);transform:translateY(-1px)}
.tool-back:active{transform:translateY(0)}
.tool-back:focus-visible{outline:3px solid #ffb4b4;outline-offset:3px}
.tool-service-banner{border-bottom:1px solid}
.tool-service-banner .container{min-height:62px;display:flex;align-items:center;gap:11px}
.tool-service-banner .el-icon{flex:0 0 auto;font-size:20px}
.tool-service-banner p{flex:1;margin:0;font-size:15px;line-height:1.7}
.tool-service-banner p b{margin-right:5px}
.tool-service-banner.safe,.tool-service-banner.ready{color:#14532d;background:#f0fdf4;border-color:#bbf7d0}
.tool-service-banner.warning,.tool-service-banner.blocked{color:#9a3412;background:#fff7ed;border-color:#fed7aa}
.tool-experience-layout{display:grid;grid-template-columns:minmax(0,1fr)320px;gap:20px;align-items:start}
.tool-workspace{min-width:0;display:flex;flex-direction:column;gap:18px}
.tool-aside{min-width:0}
@media(max-width:1000px){.tool-experience-layout{grid-template-columns:1fr}.tool-aside{display:grid;grid-template-columns:1fr 1fr;gap:16px}}
@media(max-width:700px){.tool-service-banner .container{padding-top:12px;padding-bottom:12px;align-items:flex-start}.tool-service-banner .el-button{flex:0 0 auto}.tool-aside{grid-template-columns:1fr}}
</style>
