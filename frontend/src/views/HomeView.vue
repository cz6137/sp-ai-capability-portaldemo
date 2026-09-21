<script setup lang="ts">
import { Collection, MagicStick, Trophy, DataAnalysis, ArrowRight, Monitor } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
const modules = computed(() => [
  { title: '交付基线库', desc: '沿交付环节查找标准模板、检查清单与过程资产。', to: '/baseline', icon: Collection, tone: 'red', meta: '标准 · 清单 · 过程资产' },
  { title: 'Skill 能力库', desc: '发现、下载和安装经过管理的可复用能力包。', to: '/skills', icon: MagicStick, tone: 'blue', meta: '版本 · 质检 · 下载' },
  { title: 'AI 工具库', desc: '先用样例体验工具效果，再选择远程使用或本地部署。', to: '/tools', icon: Monitor, tone: 'purple', meta: '样例体验 · 受控部署' },
  { title: '标杆案例库', desc: '从优秀项目成果中复用经验、方法与交付范式。', to: '/knowledge', icon: Trophy, tone: 'green', meta: '经验 · 方法 · 交付范式' },
  ...(isAdmin.value ? [{ title: '能力统一接入', desc: '在同一入口录入 Skill 与工具，预检清单、预览页面并管理交付包。', to: '/admin/capabilities', icon: DataAnalysis, tone: 'amber', meta: '清单 · 预检 · 交付包' }] : []),
])
</script>

<template>
  <section class="home-hero">
    <div class="container home-hero-inner">
      <div class="hero-copy">
        <span class="hero-kicker">SOUTH CHINA DELIVERY · AI ENABLEMENT</span>
        <h1>华南大区<br><em>AI 赋能交付</em>能力集成平台</h1>
        <p>以交付流程为主线，共建可复用的 AI 能力资产。模板即用、案例可鉴、知识共享。</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push('/baseline')">浏览交付基线 <el-icon><ArrowRight /></el-icon></el-button>
          <el-button size="large" :icon="Monitor" @click="router.push('/tools')">体验 AI 工具</el-button>
        </div>
      </div>
    </div>
  </section>

  <section class="section home-modules">
    <div class="container">
      <div class="section-heading">
        <div><h2>能力工作入口</h2><p>从资产复用到能力共建，覆盖项目交付全流程。</p></div>
      </div>
      <div class="module-grid">
        <RouterLink v-for="item in modules" :key="item.to" :to="item.to" class="module-card" :class="`tone-${item.tone}`">
          <div class="module-icon"><el-icon><component :is="item.icon" /></el-icon></div>
          <span class="module-meta">{{ item.meta }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
          <b>进入模块 <el-icon><ArrowRight /></el-icon></b>
        </RouterLink>
      </div>
    </div>
  </section>

</template>

<style scoped>
.home-hero { min-height: 520px; display: flex; align-items: center; color: white; background: linear-gradient(110deg, rgba(17,24,39,.98), rgba(49,28,32,.95)), radial-gradient(circle at 80% 10%, #a32d2d, transparent 48%); border-bottom: 5px solid var(--brand); }
.home-hero-inner { display: flex; align-items: center; padding: 56px 0; }
.hero-kicker { color: #fca5a5; font-size: 12px; font-weight: 700; }
.hero-copy h1 { margin: 14px 0 20px; font-size: clamp(42px, 5.2vw, 62px); line-height: 1.08; letter-spacing: 0; }
.hero-copy h1 em { color: #fecaca; font-style: normal; }
.hero-copy p { max-width: 650px; color: #cbd5e1; font-size: 17px; line-height: 1.9; }
.hero-actions { margin-top: 30px; display: flex; gap: 12px; }
.module-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; }
.module-card { min-height: 270px; padding: 24px; display: flex; flex-direction: column; background: white; border: 1px solid var(--line); border-top: 3px solid var(--accent); border-radius: 6px; transition: transform .2s, box-shadow .2s; }
.module-card:hover { transform: translateY(-4px); box-shadow: var(--shadow); }
.tone-red { --accent: #a32d2d; --soft: #fcebec; }.tone-blue { --accent: #2563eb; --soft: #dbeafe; }.tone-purple { --accent: #7c3aed; --soft: #ede9fe; }.tone-green { --accent: #047857; --soft: #d1fae5; }.tone-amber { --accent: #b45309; --soft: #fef3c7; }
.module-icon { width: 44px; height: 44px; display: grid; place-items: center; color: var(--accent); background: var(--soft); border-radius: 6px; font-size: 22px; }
.module-meta { align-self: flex-end; margin-top: -34px; color: var(--muted); font-size: 12px; }
.module-card h3 { margin: 30px 0 10px; font-size: 20px; }
.module-card p { color: var(--muted); line-height: 1.75; font-size: 14px; }
.module-card b { margin-top: auto; display: flex; align-items: center; gap: 6px; color: var(--accent); font-size: 13px; }
@media (max-width: 1100px) { .module-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 980px) { .module-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .home-hero { min-height: auto; }.home-hero-inner { padding: 45px 0; }.hero-copy h1 { font-size: 38px; }.hero-copy p { font-size: 15px; }.hero-actions { align-items: stretch; flex-direction: column; }.module-grid { grid-template-columns: 1fr; } }
</style>
