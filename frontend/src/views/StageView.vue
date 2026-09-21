<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import MaterialLibrary from '../components/MaterialLibrary.vue'
import { assetsByStage, stageByNumber, baselineState, STAGE_NAMES } from '../data'
import { stageMaterialGroups } from '../materialCatalog'
import outlines from '../data/stage-outline.json'

const route = useRoute()
const keyword = ref('')
const type = ref('all')
const stageNumber = computed(() => Number(route.params.id))
const stage = computed(() => stageByNumber(stageNumber.value))
const allAssets = computed(() => assetsByStage(stageNumber.value))
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return allAssets.value.filter(item => (type.value === 'all' || item.type === type.value)
    && (!query || [item.name, item.sub_name].join(' ').toLowerCase().includes(query)))
})
const groups = computed(() => stageMaterialGroups(filtered.value,
  outlines.stages.find(item => item.stage === stageNumber.value)?.groups || [], 'stage-' + stageNumber.value))

async function focusAsset() {
  const target = route.query.asset
  if (!target) return
  await nextTick()
  const elements = [...document.querySelectorAll<HTMLElement>('.asset-item')]
  const element = elements.find(el => el.dataset.mediaId === String(target)) || elements.find(el => el.textContent?.includes(String(target)))
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element?.classList.add('asset-highlight')
  setTimeout(() => element?.classList.remove('asset-highlight'), 2200)
}
onMounted(focusAsset)
watch(() => route.params.id, () => { keyword.value = ''; type.value = 'all' })
watch(() => route.query.asset, async () => { keyword.value = ''; type.value = 'all'; await focusAsset() })
watch(allAssets, focusAsset)
</script>

<template>
  <MaterialLibrary v-loading="baselineState.loading" :title="STAGE_NAMES[stageNumber] || stage?.name || '交付阶段'" :description="stage?.description || '按业务事项查找本阶段的交付材料。'" back-to="/baseline" back-label="交付基线库" :items="allAssets" :groups="groups" :empty-text="allAssets.length ? '当前条件下没有匹配材料' : '当前阶段暂无材料'">
    <template #filters>
      <el-radio-group v-model="type" aria-label="材料类型">
        <el-radio-button value="all">全部</el-radio-button><el-radio-button value="T">SD 模板</el-radio-button><el-radio-button value="C">卓越案例</el-radio-button><el-radio-button v-if="allAssets.some(item => item.type === 'A')" value="A">AI 工具</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索当前阶段材料" />
    </template>
  </MaterialLibrary>
</template>

<style scoped>
:global(.asset-highlight){outline:3px solid rgba(163,45,45,.35);box-shadow:0 8px 25px rgba(163,45,45,.16)!important}
</style>
