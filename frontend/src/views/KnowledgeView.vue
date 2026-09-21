<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, FolderOpened, Search } from '@element-plus/icons-vue'
import PageHero from '../components/PageHero.vue'
import BaselineSourceNotice from '../components/BaselineSourceNotice.vue'
import { assets, baselineState } from '../data'
import { buildCaseCatalog } from '../caseCatalog'
import tags from '../data/case-domain-tags.json'

const keyword = ref('')
const categories = computed(() => buildCaseCatalog(assets, tags.tags))
const filtered = computed(() => categories.value.filter(item => !keyword.value.trim() || [item.name, item.description, ...item.items.map(asset => asset.name)].join(' ').toLowerCase().includes(keyword.value.trim().toLowerCase())))
const total = computed(() => categories.value.reduce((sum, item) => sum + item.count, 0))
const stageCount = computed(() => new Set(assets.filter(item => item.type === 'C').map(item => item.stage_num)).size)
</script>

<template>
  <PageHero eyebrow="BEST PRACTICES" title="标杆案例库" description="按业务领域组织交付案例，并结合交付环节筛选，复用项目成果、过程文档和实践经验。" />
  <section class="section"><div class="container">
    <BaselineSourceNotice />
    <template v-if="!baselineState.error">
      <div class="case-summary"><div><b>{{ categories.length }}</b><span>业务分类</span></div><div><b>{{ total }}</b><span>案例材料</span></div><div><b>{{ stageCount }}</b><span>覆盖环节</span></div></div>
      <div class="case-toolbar"><p>业务领域与交付环节分别组织；数量按实际案例材料统计。</p><el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索业务分类或项目" /></div>
      <div v-loading="baselineState.loading" class="category-grid">
        <RouterLink v-for="item in filtered" :key="item.code" :to="'/knowledge/' + item.code" class="category-item">
          <h2><el-icon><FolderOpened /></el-icon>{{ item.name }}</h2>
          <p>该业务领域有 <b>{{ item.count }}</b> 项案例材料，覆盖 <b>{{ item.stageCount }}</b> 个交付环节。</p>
          <p v-if="item.unclassified" class="classification-note">其中 {{ item.unclassified }} 项尚未标注业务领域。</p>
          <span>查看案例 <el-icon><ArrowRight /></el-icon></span>
        </RouterLink>
      </div>
      <el-empty v-if="!baselineState.loading && !filtered.length" description="暂无匹配的案例分类" />
    </template>
  </div></section>
</template>

<style scoped>
.case-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:24px}.case-summary>div{display:flex;flex-direction:column;align-items:center;gap:10px;padding:24px;border:1px solid var(--line);border-radius:8px;background:white}.case-summary b{font-size:30px;color:#ad2d2d}.case-summary span{font-size:16px;color:var(--muted)}.case-toolbar{display:flex;align-items:center;justify-content:space-between;gap:24px;margin-bottom:22px}.case-toolbar p{font-size:16px;color:var(--muted);line-height:1.7}.case-toolbar .el-input{max-width:380px}.category-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.category-item{display:flex;flex-direction:column;align-items:flex-start;min-height:190px;padding:24px;border:1px solid var(--line);border-top:3px solid #ad2d2d;border-radius:8px;background:white}.category-item:hover{border-color:#ad2d2d;box-shadow:var(--shadow)}.category-item h2{display:flex;align-items:center;gap:10px;margin:0 0 18px;font-size:21px;line-height:1.5}.category-item h2 .el-icon{color:#ad2d2d}.category-item p{margin:0 0 18px;font-size:16px;color:var(--muted);line-height:1.8}.category-item b{color:#ad2d2d}.category-item>span{display:flex;align-items:center;gap:8px;margin-top:auto;color:#ad2d2d;font-size:16px;font-weight:700}.category-item .classification-note{font-size:14px}@media(max-width:1050px){.category-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.category-grid{grid-template-columns:1fr}.case-toolbar{align-items:stretch;flex-direction:column;gap:10px}.case-toolbar .el-input{max-width:none}.case-summary{gap:8px}.case-summary>div{padding:18px 8px}}
</style>
