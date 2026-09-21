<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import MaterialLibrary from '../components/MaterialLibrary.vue'
import { assets, baselineState, STAGE_NAMES } from '../data'
import { buildCaseCatalog } from '../caseCatalog'
import { materialSections } from '../materialCatalog'
import tags from '../data/case-domain-tags.json'

const route = useRoute()
const keyword = ref('')
const stage = ref<number | ''>('')
const category = computed(() => buildCaseCatalog(assets, tags.tags).find(item => item.code === route.params.code))
const items = computed(() => category.value?.items || [])
const stageOptions = computed(() => [...new Set(items.value.map(item => item.stage_num))].sort((a,b) => a-b))
const filtered = computed(() => items.value.filter(item => (stage.value === '' || item.stage_num === stage.value) && (!keyword.value.trim() || [item.name, item.sub_name, item.stage_name].join(' ').toLowerCase().includes(keyword.value.trim().toLowerCase()))))
const groups = computed(() => stageOptions.value.map(number => ({
  id: 'case-stage-' + number, title: STAGE_NAMES[number] || '交付阶段 ' + number, href: '/stage/' + number,
  sections: materialSections(filtered.value.filter(item => item.stage_num === number), 'case-' + route.params.code + '-' + number),
})).filter(group => group.sections.length))
watch(() => route.params.code, () => { keyword.value = ''; stage.value = '' })
</script>

<template>
  <MaterialLibrary v-loading="baselineState.loading" :title="category?.name || '案例分类不存在'" :description="category?.description || '请返回案例库选择业务分类。'" back-to="/knowledge" back-label="标杆案例库" :items="items" :groups="groups" :empty-text="items.length ? '当前筛选条件下没有匹配材料' : '当前分类暂无案例材料'">
    <template #filters>
      <el-select v-model="stage" aria-label="交付环节" placeholder="全部交付环节"><el-option label="全部交付环节" value="" /><el-option v-for="number in stageOptions" :key="number" :label="number + '-' + STAGE_NAMES[number]" :value="number" /></el-select>
      <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索项目、材料或交付阶段" />
    </template>
    <template #notice><p v-if="category?.unclassified" class="classification-note">{{ category.unclassified }} 项材料尚未标注业务领域，暂列通用/跨域。</p></template>
  </MaterialLibrary>
</template>

<style scoped>
.classification-note{font-size:16px;color:#80551b;line-height:1.8}
</style>
