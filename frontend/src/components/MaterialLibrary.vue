<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'
import AssetItem from './AssetItem.vue'
import BaselineSourceNotice from './BaselineSourceNotice.vue'
import { materialTypes, type MaterialGroup } from '../materialCatalog'
import type { Asset } from '../types'

const props = defineProps<{
  title: string; description: string; backTo: string; backLabel: string
  items: Asset[]; groups: MaterialGroup[]; emptyText: string
}>()
const root = ref<HTMLElement>()
const active = ref('')
const count = (group: MaterialGroup) => group.sections.reduce((sum, section) => sum + section.items.length, 0)
const visibleCount = computed(() => props.groups.reduce((sum, group) => sum + count(group), 0))
const stats = computed(() => props.items.length && props.items.every(item => item.type === 'C')
  ? [{ type: 'stage', label: '覆盖环节', count: new Set(props.items.map(item => item.stage_num)).size },
    { type: 'section', label: '材料类别', count: new Set(props.items.map(item => item.sub_name || '其他材料')).size }]
  : materialTypes.map(kind => ({ ...kind, count: props.items.filter(item => item.type === kind.type).length })).filter(item => item.count))
let frame = 0
function updateActive() {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const sections = [...(root.value?.querySelectorAll<HTMLElement>('[data-material-section]') || [])]
    const above = sections.filter(section => section.getBoundingClientRect().top <= 160)
    active.value = (above[above.length - 1] || sections[0])?.id || ''
  })
}
function jump(event: MouseEvent, id: string) {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
onMounted(() => { window.addEventListener('scroll', updateActive, { passive: true }); updateActive() })
onUnmounted(() => { window.removeEventListener('scroll', updateActive); cancelAnimationFrame(frame) })
watch(() => props.groups, async () => { await nextTick(); updateActive() })
</script>

<template>
  <div ref="root" class="material-page">
    <div class="library-breadcrumb"><RouterLink :to="backTo">← {{ backLabel }}</RouterLink><span>/</span><span>{{ title }}</span></div>
    <div class="library-layout">
      <aside class="library-sidebar surface">
        <div class="directory-heading"><el-icon><FolderOpened /></el-icon><div><strong>{{ title }}</strong><span>内容目录 · {{ visibleCount }} 项材料</span></div></div>
        <nav aria-label="材料目录">
          <div v-for="(group, index) in groups" :key="group.id" class="directory-group">
            <a :href="'#' + encodeURIComponent(group.sections[0]!.id)" class="directory-parent" :class="{ active: group.sections.some(section => section.id === active) }" @click="jump($event, group.sections[0]!.id)"><span class="directory-number">{{ String(index + 1).padStart(2, '0') }}</span><span>{{ group.title }}</span><small>{{ count(group) }}</small></a>
            <template v-if="group.sections.length > 1 || group.sections[0]?.title !== group.title">
              <a v-for="section in group.sections" :key="section.id" :href="'#' + encodeURIComponent(section.id)" class="directory-child" :class="{ active: active === section.id }" :aria-current="active === section.id ? 'location' : undefined" @click="jump($event, section.id)">{{ section.title }}<small>{{ section.items.length }}</small></a>
            </template>
          </div>
          <p v-if="!groups.length" class="directory-empty">暂无匹配目录</p>
        </nav>
      </aside>

      <div class="library-content">
        <header class="library-overview surface">
          <div class="overview-heading"><div class="overview-icon"><el-icon><FolderOpened /></el-icon></div><div><span class="overview-label">交付资料 · 按业务组织</span><h1>{{ title }}</h1></div></div>
          <p class="overview-description">{{ description }}</p>
          <div class="overview-bottom"><div class="library-stats"><div><b>{{ items.length }}</b><span>材料总数</span></div><div v-for="stat in stats" :key="stat.type"><b>{{ stat.count }}</b><span>{{ stat.label }}</span></div></div><BaselineSourceNotice /></div>
        </header>
        <div class="library-filters surface"><slot name="filters" /><span class="filter-result">显示 <b>{{ visibleCount }}</b> 项</span></div>
        <slot name="notice" />
        <div class="library-groups">
          <section v-for="group in groups" :key="group.id" class="library-group">
            <header class="group-heading"><h2>{{ group.title }}</h2><span>{{ count(group) }} 项</span><RouterLink v-if="group.href" :to="group.href">查看此交付环节 →</RouterLink></header>
            <section v-for="section in group.sections" :id="section.id" :key="section.id" data-material-section class="material-section surface">
              <header class="material-section-heading"><el-icon><FolderOpened /></el-icon><h3>{{ section.title }}</h3><span>{{ section.items.length }} 项材料</span></header>
              <template v-for="kind in materialTypes" :key="kind.type">
                <div v-if="section.items.some(item => item.type === kind.type)" class="material-kind">
                  <h4 :class="'kind-' + kind.type"><span>{{ kind.label }}</span><span>{{ section.items.filter(item => item.type === kind.type).length }}</span></h4>
                  <div class="material-rows"><AssetItem v-for="item in section.items.filter(item => item.type === kind.type)" :key="item.id || item.media_id || item.name" :asset="item" :show-type="false" /></div>
                </div>
              </template>
            </section>
          </section>
        </div>
        <el-empty v-if="!groups.length" :description="emptyText" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.material-page{width:min(1320px,calc(100% - 48px));margin:0 auto;padding:28px 0 56px;color:var(--ink)}
.library-breadcrumb{display:flex;gap:12px;align-items:center;margin-bottom:24px;font-size:15px;color:var(--muted)}.library-breadcrumb a:hover{color:var(--brand)}
.library-layout{display:grid;grid-template-columns:252px minmax(0,1fr);gap:28px;align-items:start}.library-sidebar{position:sticky;top:92px;overflow:hidden}.directory-heading{display:flex;gap:12px;align-items:center;padding:22px 18px;border-bottom:1px solid var(--line)}.directory-heading>.el-icon{font-size:25px;color:var(--brand)}.directory-heading strong{font-size:18px;display:block}.directory-heading span{display:block;color:var(--muted);font-size:14px;margin-top:6px}.library-sidebar nav{padding:12px;max-height:calc(100vh - 225px);overflow:auto;scrollbar-width:thin}.directory-group{margin-bottom:8px}.directory-parent{display:flex;align-items:baseline;gap:9px;padding:11px 8px;font-size:16px;font-weight:700;border-radius:5px}.directory-number{font-size:12px;color:var(--brand);font-variant-numeric:tabular-nums}.directory-parent>span:nth-child(2){flex:1}.directory-group small{color:var(--muted);font-size:13px;font-weight:400;margin-left:auto}.directory-child{display:flex;gap:8px;margin:2px 0 2px 25px;padding:8px 10px;border-left:2px solid var(--line);font-size:15px;line-height:1.5;color:var(--muted)}.directory-parent:hover,.directory-parent.active{background:#f8eded;color:var(--brand)}.directory-child:hover,.directory-child.active{color:var(--brand);border-left-color:var(--brand);background:#fdf7f7}.directory-empty{font-size:15px;color:var(--muted);padding:0 10px}
.library-content{min-width:0}.library-overview{padding:28px 30px;border-top:4px solid var(--brand)}.overview-heading{display:flex;gap:16px;align-items:center}.overview-icon{width:48px;height:48px;display:grid;place-items:center;border-radius:12px;background:var(--brand-soft);color:var(--brand);font-size:28px}.overview-label{font-size:14px;color:var(--muted)}.library-overview h1{font-size:32px;line-height:1.3;margin:5px 0 0}.overview-description{font-size:16px;line-height:1.8;color:var(--muted);margin:18px 0 22px}.overview-bottom{display:flex;align-items:center;justify-content:space-between;gap:20px;flex-wrap:wrap}.library-stats{display:flex;gap:0}.library-stats>div{min-width:106px;padding:0 24px;border-right:1px solid var(--line)}.library-stats>div:first-child{padding-left:0}.library-stats>div:last-child{border-right:0}.library-stats b{font-size:28px;display:block;color:var(--brand);line-height:1.3}.library-stats span{font-size:14px;display:block;margin-top:6px;color:var(--muted)}.overview-bottom :deep(.baseline-source){margin:0}.overview-bottom :deep(.baseline-source>a){font-size:15px}
.library-filters{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:16px 18px;margin:22px 0 28px}.library-filters :deep(.el-input){flex:1;min-width:190px}.library-filters :deep(.el-select){width:190px}.library-filters :deep(.el-input__inner),.library-filters :deep(.el-select__placeholder),.library-filters :deep(.el-radio-button__inner){font-size:15px}.filter-result{font-size:14px;white-space:nowrap;color:var(--muted)}.filter-result b{color:var(--ink)}
.library-groups{display:flex;flex-direction:column;gap:30px}.group-heading{display:flex;align-items:center;gap:12px;border-left:4px solid var(--brand);background:#eaf0f7;border-radius:0 6px 6px 0;padding:12px 16px;margin-bottom:16px}.group-heading h2{margin:0;font-size:20px}.group-heading>span{font-size:14px;color:var(--muted)}.group-heading>a{margin-left:auto;font-size:14px;color:var(--brand)}.material-section{padding:22px;margin-top:16px;scroll-margin-top:92px}.material-section-heading{display:flex;align-items:center;gap:10px;padding-bottom:17px;border-bottom:1px solid var(--line)}.material-section-heading>.el-icon{color:#c18a28;font-size:22px}.material-section-heading h3{font-size:18px;margin:0;line-height:1.5;flex:1}.material-section-heading>span{font-size:14px;color:var(--muted)}.material-kind{margin-top:20px}.material-kind>h4{display:flex;gap:8px;align-items:center;margin:0 0 10px;font-size:14px;font-weight:500;color:var(--muted)}.material-kind>h4:before{content:'';width:6px;height:6px;border-radius:50%;background:#2563eb}.material-kind>h4.kind-C:before{background:#0f766e}.material-kind>h4.kind-A:before{background:#c56a13}.material-kind>h4>span:last-child{padding:1px 7px;border-radius:10px;background:#f0f3f7;font-size:12px}.material-rows{display:flex;flex-direction:column;gap:8px}
@media(max-width:1050px){.library-layout{grid-template-columns:220px minmax(0,1fr);gap:20px}.library-overview{padding:24px}.library-stats>div{min-width:84px;padding:0 16px}.material-section{padding:18px}}
@media(max-width:800px){.library-layout{grid-template-columns:1fr}.library-sidebar{position:static}.library-sidebar nav{max-height:230px}.directory-heading{padding:15px 18px}.library-overview h1{font-size:28px}.material-page{width:calc(100% - 24px);padding-top:20px}.group-heading{flex-wrap:wrap}.material-section{scroll-margin-top:80px}}
@media(max-width:480px){.library-overview{padding:20px}.library-stats>div{min-width:72px;padding:0 12px}.library-filters{padding:12px}.library-filters :deep(.el-radio-group){width:100%}.material-section{padding:14px}.material-section-heading{flex-wrap:wrap}.material-section-heading>span{width:100%;margin-left:32px}.group-heading>a{width:100%;margin-left:0}}
@media(prefers-reduced-motion:reduce){:global(html){scroll-behavior:auto}}
</style>
