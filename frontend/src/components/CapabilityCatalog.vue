<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Box, Download, Lock, Search, Tools, View } from '@element-plus/icons-vue'
import PageHero from './PageHero.vue'
import CapabilityDownloadButton from './CapabilityDownloadButton.vue'
import { toolExperienceAccess } from '../tools/runtime/availability'
import { capabilityReader } from '../capabilityReader'
import { capabilityErrorMessage, type CapabilitySelection } from '../capabilityAccess'
import { STAGE_NAMES } from '../baselineAccess'
import { externalCapabilityCandidates } from '../data/external-capability-candidates'
import { metricsOf } from '../data/capability-metrics'
import type { CapabilityKindV2, CapabilityManifestV2 } from '../capabilitySchema'

const props = defineProps<{ kind: CapabilityKindV2 }>()
const router = useRouter()
const manifests = ref<CapabilityManifestV2[]>([])
const selections = ref<CapabilitySelection[]>([])
const loading = ref(false)
const localPreview = capabilityReader.source === 'bundled'
const error = ref('')
let request = 0
const keyword = ref('')
const activeStage = ref<number | ''>('')
const isSkill = computed(() => props.kind === 'skill')
const externalCandidates = computed(() => externalCapabilityCandidates.filter(item => item.kind === props.kind))
const title = computed(() => isSkill.value ? 'Skill 能力库' : 'AI 工具库')
const eyebrow = computed(() => isSkill.value ? 'REUSABLE CAPABILITY PACKAGES' : 'AI TOOL EXPERIENCE')
const description = computed(() => isSkill.value
  ? '集中查看能力包的适用场景、输入材料、执行流程、质量边界和交付方式。'
  : '选择工具进入体验模式，完成材料选择、处理、预览和下载；也可以先查看适用场景和使用说明。')
const stageFilters = computed(() => STAGE_NAMES.map((name, id) => ({
  id,
  name,
  count: manifests.value.filter(item => item.classification.stageIds.includes(id)).length,
})))
const downloadableCount = computed(() => manifests.value.filter(item => !!item.delivery.package).length)
const onlineCount = computed(() => manifests.value.filter(item => item.delivery.mode !== 'package').length)
const toolExperienceCount = computed(() => manifests.value.reduce((total, item) => total + metricsOf(item.identity.slug).experiences, 0))
const totalDownloadCount = computed(() => manifests.value.reduce((total, item) => total + metricsOf(item.identity.slug).downloads, 0))
const latestUpdatedAt = computed(() => manifests.value
  .map(item => item.identity.updatedAt)
  .filter(Boolean)
  .sort((left, right) => right.localeCompare(left))[0] || '—')
const filtered = computed(() => manifests.value.filter(item => {
  const value = `${item.identity.name} ${item.identity.slug} ${item.identity.tagline} ${item.identity.description} ${item.classification.tags.join(' ')} ${item.usage.scenarios.join(' ')} ${item.usage.inputs.map(input => `${input.name} ${input.detail}`).join(' ')}`.toLowerCase()
  const matchesKeyword = !keyword.value.trim() || value.includes(keyword.value.trim().toLowerCase())
  const matchesStage = activeStage.value === '' || item.classification.stageIds.includes(activeStage.value)
  return matchesKeyword && matchesStage
}))

const downloadRanking = computed(() => manifests.value
  .map(item => ({ item, count: metricsOf(item.identity.slug).downloads }))
  .sort((left, right) => right.count - left.count))
const toolRanking = computed(() => manifests.value
  .map(item => ({ item, metrics: metricsOf(item.identity.slug) }))
  .sort((left, right) => right.metrics.experiences - left.metrics.experiences))
const formatDownloads = (count: number) => new Intl.NumberFormat('zh-CN').format(count)

const statusLabels = { DRAFT: '草稿', IN_REVIEW: '待审核', PUBLISHED: '已发布', ARCHIVED: '已归档' }
const displayStatus = (item: CapabilityManifestV2) => localPreview ? 'DEMO' : statusLabels[item.governance.status]

async function load() {
  const current = ++request
  loading.value = true; error.value = ''; manifests.value = []; selections.value = []
  try {
    const records = await capabilityReader.list(props.kind)
    if (current === request) { selections.value = records; manifests.value = records.map(record => record.manifest) }
  } catch (cause) {
    if (current === request) error.value = capabilityErrorMessage(cause)
  } finally { if (current === request) loading.value = false }
}

function open(item: CapabilityManifestV2) { router.push(`/capabilities/${item.identity.slug}`) }
function experience(item: CapabilityManifestV2) { return toolExperienceAccess({ manifest: item, source: capabilityReader.source }) }
function tryTool(item: CapabilityManifestV2) { if (!experience(item).reason) router.push(`/tools/${item.identity.slug}`) }
function openCard(item: CapabilityManifestV2) { if (item.kind === 'tool' && !experience(item).reason) tryTool(item); else open(item) }
watch(() => props.kind, () => { keyword.value = ''; activeStage.value = ''; load() }, { immediate: true })
</script>

<template>
  <PageHero :eyebrow="eyebrow" :title="title" :description="description">
    <div class="hero-note"><el-icon><Lock /></el-icon>{{ isSkill ? '下载和正式使用前，请先确认版本、适用边界与人工复核要求。' : '在线处理可能使用外部服务；提交前必须确认页面列明的数据边界。' }}</div>
  </PageHero>
  <section class="section catalog-section"><div class="container">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button text @click="load">重新读取</el-button></el-alert>
    <div class="directory-overview">
      <div class="directory-stats">
        <strong>{{ isSkill ? '能力目录' : '工具目录' }}</strong>
        <span><b>{{ manifests.length }}</b> 项{{ isSkill ? '能力' : '工具' }}</span>
        <span v-if="isSkill"><b>{{ downloadableCount }}</b> 项可下载</span>
        <span v-else><b>{{ onlineCount }}</b> 项可在线使用</span>
        <span v-if="!isSkill"><b>{{ formatDownloads(toolExperienceCount) }}</b> 次体验</span>
        <span><b>{{ formatDownloads(totalDownloadCount) }}</b> 次下载</span>
        <span>最近更新 <b>{{ latestUpdatedAt }}</b></span>
      </div>
      <div class="stage-select">
        <label for="capability-stage">交付环节</label>
        <el-select id="capability-stage" v-model="activeStage" size="large" aria-label="按九大交付环节筛选">
          <el-option :label="`全部交付环节（${manifests.length}）`" value="" />
          <el-option v-for="stage in stageFilters" :key="stage.id" :label="`${stage.name}（${stage.count}）`" :value="stage.id" />
        </el-select>
      </div>
    </div>
    <div class="catalog-layout">
      <div v-loading="loading" class="capability-list">
        <article v-for="item in filtered" :key="item.identity.slug" class="capability-card" :style="{ '--accent': item.identity.accent || '#ad2d2d' }" @click="openCard(item)">
          <div class="card-rail"><el-icon><component :is="isSkill ? Box : Tools" /></el-icon><small>{{ isSkill ? 'Skill 能力包' : '业务工具' }}</small></div>
          <div class="card-main"><div class="card-heading"><div><span class="status">{{ displayStatus(item) }}</span><h3>{{ item.identity.name }}</h3><p class="tagline">{{ item.identity.tagline }}</p></div><div class="version"><small>版本</small><b>v{{ item.identity.version }}</b></div></div><p class="description">{{ item.identity.description }}</p><div class="io-flow"><div><span>需要提供</span><b>{{ item.usage.inputs.map(input => input.name).join('、') }}</b></div><el-icon><ArrowRight /></el-icon><div><span>形成结果</span><b>{{ item.usage.outputs.map(output => output.name).join('、') }}</b></div></div><footer><div class="card-meta"><p>维护人：{{ item.identity.maintainer }}　·　更新：{{ item.identity.updatedAt }}　·　交付：{{ { package: '能力包下载', online: '在线使用', hybrid: '在线使用与能力包' }[item.delivery.mode] }}</p><div class="capability-metrics"><span v-if="!isSkill"><el-icon><View /></el-icon><b>{{ formatDownloads(metricsOf(item.identity.slug).experiences) }}</b> 次体验</span><span><el-icon><Download /></el-icon><b>{{ formatDownloads(metricsOf(item.identity.slug).downloads) }}</b> 次下载</span></div></div><div class="card-actions"><CapabilityDownloadButton v-if="item.delivery.package" :selected="selections.find(record => record.manifest.identity.slug === item.identity.slug)!" :primary="isSkill" /><el-button v-if="!isSkill" type="primary" :disabled="!!experience(item).reason" @click.stop="tryTool(item)">进入体验模式<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button><el-button plain @click.stop="open(item)">查看完整说明</el-button><span v-if="!isSkill && experience(item).reason" class="entry-reason">{{ experience(item).reason }}</span></div></footer></div>
        </article>
        <el-empty v-if="!loading && !error && !filtered.length" :image-size="90" :description="keyword.trim() || activeStage !== '' ? '当前筛选条件下没有匹配结果' : `当前没有可展示的${isSkill ? ' Skill' : '工具'}`" />
      </div>
      <aside class="catalog-sidebar">
        <section class="sidebar-panel search-panel">
          <div class="panel-heading"><div><span>查找能力</span><h3>搜索</h3></div><el-icon><Search /></el-icon></div>
          <el-input v-model="keyword" clearable :prefix-icon="Search" :placeholder="isSkill ? '场景、材料或能力名称' : '场景、材料或工具名称'" />
          <p>{{ keyword.trim() ? `找到 ${filtered.length} 项匹配结果` : `当前共 ${manifests.length} 项${isSkill ? '能力' : '工具'}` }}</p>
        </section>
        <section v-if="isSkill" class="sidebar-panel ranking-panel">
          <div class="panel-heading"><div><span>热门能力</span><h3>下载量排行</h3></div><el-icon><Download /></el-icon></div>
          <div class="demo-label">演示数据</div>
          <ol class="ranking-list">
            <li v-for="(rank, index) in downloadRanking" :key="rank.item.identity.slug" @click="open(rank.item)">
              <b class="rank-number">{{ String(index + 1).padStart(2, '0') }}</b>
              <div><strong>{{ rank.item.identity.name }}</strong><span>{{ formatDownloads(rank.count) }} 次下载</span></div>
              <el-icon><ArrowRight /></el-icon>
            </li>
          </ol>
          <p class="ranking-note">当前数值用于版式与交互演示，尚未接入真实下载记录。</p>
        </section>
        <section v-else class="sidebar-panel ranking-panel">
          <div class="panel-heading"><div><span>使用情况</span><h3>热门工具</h3></div><el-icon><View /></el-icon></div>
          <div class="demo-label">统计样例</div>
          <ol class="ranking-list">
            <li v-for="(rank, index) in toolRanking" :key="rank.item.identity.slug" @click="tryTool(rank.item)">
              <b class="rank-number">{{ String(index + 1).padStart(2, '0') }}</b>
              <div><strong>{{ rank.item.identity.name }}</strong><span>{{ formatDownloads(rank.metrics.experiences) }} 次体验 · {{ formatDownloads(rank.metrics.downloads) }} 次下载</span></div>
              <el-icon><ArrowRight /></el-icon>
            </li>
          </ol>
          <p class="ranking-note">当前用于页面展示，接入统计服务后更新为实际使用记录。</p>
        </section>
      </aside>
    </div>
    <section v-if="externalCandidates.length" class="external-section">
      <header>
        <div><span>{{ isSkill ? '社区来源' : '外部体验' }}</span><h2>{{ isSkill ? '可继续适配的 Skill' : '可直接试用的工具' }}</h2></div>
        <p>{{ isSkill ? '以下项目尚未纳入平台能力包，完成依赖、许可和中文说明检查后再进入上传流程。' : '点击后将在新窗口打开第三方服务；上传材料前请先阅读对方的数据与隐私条款。' }}</p>
      </header>
      <div class="external-grid">
        <article v-for="candidate in externalCandidates" :key="candidate.sourceUrl" class="external-card">
          <div class="external-card-top"><span class="external-status">{{ candidate.statusLabel }}</span><small>{{ candidate.source }}</small></div>
          <h3>{{ candidate.name }}</h3>
          <p>{{ candidate.summary }}</p>
          <div class="external-tags"><span v-for="tag in candidate.tags" :key="tag">{{ tag }}</span></div>
          <footer><small>许可：{{ candidate.license }}</small><a :href="candidate.sourceUrl" target="_blank" rel="noopener noreferrer">{{ candidate.actionLabel }}<el-icon><ArrowRight /></el-icon></a></footer>
        </article>
      </div>
    </section>
  </div></section>
</template>

<style scoped>
.card-actions{display:flex;align-items:flex-start;gap:10px;flex-wrap:wrap;flex-shrink:0}.card-actions .el-button{margin-left:0}.entry-reason{width:100%;font-size:14px;color:#94510b}
.hero-note{margin-top:22px;display:flex;align-items:center;gap:8px;color:#dbe4ef;font-size:14px}.catalog-section{background:#f5f7fa}.directory-overview{margin-bottom:22px;padding:20px 22px;border:1px solid #dfe4eb;border-radius:10px;display:flex;align-items:center;justify-content:space-between;gap:24px;background:#fff}.directory-stats{display:flex;align-items:center;gap:0;color:#687386;font-size:15px;white-space:nowrap}.directory-stats strong{margin-right:20px;color:#172033;font-size:21px}.directory-stats span{padding:0 16px;border-left:1px solid #e4e8ee}.directory-stats b{color:#263349;font-size:16px;font-variant-numeric:tabular-nums}.stage-select{min-width:280px;display:flex;align-items:center;gap:12px}.stage-select label{flex:none;color:#425066;font-size:15px;font-weight:700}.stage-select .el-select{width:220px}.stage-select :deep(.el-select__wrapper){min-height:44px;font-size:15px}.catalog-layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;align-items:start;gap:24px}.capability-list{min-width:0;min-height:280px;display:flex;flex-direction:column;gap:20px}.catalog-sidebar{position:sticky;top:92px;display:flex;flex-direction:column;gap:18px}.sidebar-panel{position:relative;padding:22px;border:1px solid #dfe4eb;border-radius:10px;background:#fff;box-shadow:0 8px 24px rgba(23,32,51,.04)}.panel-heading{margin-bottom:18px;display:flex;align-items:center;justify-content:space-between;gap:16px}.panel-heading span{color:#8b95a5;font-size:12px;font-weight:700;letter-spacing:1px}.panel-heading h3{margin:4px 0 0;color:#172033;font-size:20px}.panel-heading>.el-icon{width:38px;height:38px;border-radius:9px;display:grid;place-items:center;color:var(--brand);background:#f8eded;font-size:18px}.search-panel>p{margin:11px 0 0;color:#7b8492;font-size:12px}.demo-label{position:absolute;right:20px;top:73px;padding:3px 7px;border-radius:3px;color:#85510a;background:#fff3d3;font-size:11px;font-weight:700}.ranking-list{margin:0;padding:0;list-style:none}.ranking-list li{padding:15px 0;border-top:1px solid #edf0f4;display:grid;grid-template-columns:32px minmax(0,1fr) auto;align-items:center;gap:10px;cursor:pointer}.ranking-list li:first-child{border-top:0}.ranking-list li:hover strong{color:var(--brand)}.rank-number{color:#9ba4b2;font-size:13px;font-variant-numeric:tabular-nums}.ranking-list li:nth-child(-n+3) .rank-number{color:var(--brand);font-size:17px}.ranking-list div{min-width:0;display:flex;flex-direction:column;gap:5px}.ranking-list strong{overflow:hidden;color:#263349;font-size:14px;line-height:1.45;text-overflow:ellipsis;white-space:nowrap;transition:.15s}.ranking-list span{color:#7b8492;font-size:12px}.ranking-list .el-icon{color:#a5adba}.ranking-note{margin:12px 0 0;padding-top:14px;border-top:1px solid #edf0f4;color:#8b95a5;font-size:12px;line-height:1.65}.capability-card{display:grid;grid-template-columns:90px minmax(0,1fr);overflow:hidden;border:1px solid var(--line);border-radius:10px;background:#fff;cursor:pointer;transition:.18s ease}.capability-card:hover{transform:translateY(-2px);border-color:#c6ceda;box-shadow:0 14px 34px rgba(23,32,51,.1)}.card-rail{padding:25px 12px;display:flex;flex-direction:column;align-items:center;gap:18px;color:#fff;background:linear-gradient(155deg,var(--accent),color-mix(in srgb,var(--accent) 62%,#172033))}.card-rail>.el-icon{width:50px;height:50px;border:1px solid rgba(255,255,255,.5);border-radius:14px;display:grid;place-items:center;font-size:25px}.card-rail small{writing-mode:vertical-rl;letter-spacing:3px;font-size:12px;font-weight:700}.card-main{min-width:0;padding:26px 26px}.card-heading{display:flex;justify-content:space-between;gap:20px}.status{display:inline-block;padding:4px 9px;border-radius:4px;color:#7c4a08;background:#fff4d6;font-size:13px;font-weight:700}.card-heading h3{margin:10px 0 6px;font-size:25px}.tagline{margin:0;color:#475569;font-size:16px;line-height:1.7}.version{min-width:92px;padding-left:18px;border-left:1px solid var(--line);display:flex;flex-direction:column;align-items:flex-end}.version small{color:#94a3b8;font-size:12px}.version b{font-size:20px}.description{max-width:1050px;margin:16px 0;color:#4b5563;font-size:15px;line-height:1.8}.io-flow{margin:16px 0;padding:15px 17px;border:1px solid #e5e9ef;border-radius:7px;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:14px;background:linear-gradient(90deg,#f7f9fc,#fbfcfd)}.io-flow>div{display:flex;flex-direction:column;gap:5px}.io-flow span{color:#8993a3;font-size:12px}.io-flow b{font-size:14px;line-height:1.6}.io-flow>.el-icon{color:var(--accent);font-size:20px}.card-main footer{padding-top:16px;border-top:1px solid var(--line);display:flex;align-items:flex-start;justify-content:space-between;gap:18px;flex-direction:column}.card-main footer p{margin:0;color:#6b7280;font-size:13px;line-height:1.6}.card-meta{width:100%;display:flex;align-items:center;justify-content:space-between;gap:18px}.tool-metrics{display:flex;align-items:center;gap:18px;flex:none}.tool-metrics span{display:inline-flex;align-items:center;gap:6px;color:#667085;font-size:13px}.tool-metrics .el-icon{color:var(--accent);font-size:16px}.tool-metrics b{color:#263349;font-size:15px;font-variant-numeric:tabular-nums}
@media(max-width:1100px){.directory-overview{align-items:flex-start;flex-direction:column}.stage-select{width:100%}.stage-select .el-select{width:min(360px,100%)}.catalog-layout{grid-template-columns:1fr}.catalog-sidebar{position:static;order:-1;display:grid;grid-template-columns:1fr 1fr}.catalog-sidebar .search-panel:only-child{grid-column:1/-1}.ranking-panel{grid-row:span 2}.demo-label{top:73px}}
@media(max-width:700px){.directory-stats{align-items:flex-start;flex-wrap:wrap;white-space:normal}.directory-stats strong{width:100%;margin:0 0 10px}.directory-stats span{margin:4px 0;padding:0 10px}.directory-stats span:nth-of-type(1){padding-left:0;border-left:0}.catalog-sidebar{display:flex}.capability-card{grid-template-columns:1fr}.card-rail{padding:14px 18px;flex-direction:row}.card-rail>.el-icon{width:42px;height:42px}.card-rail small{writing-mode:horizontal-tb}.card-main{padding:24px 20px}.card-heading{flex-direction:column}.version{padding:0;border:0;align-items:flex-start}.io-flow{grid-template-columns:1fr}.io-flow>.el-icon{transform:rotate(90deg)}.card-main footer{align-items:flex-start;flex-direction:column}.card-meta{align-items:flex-start;flex-direction:column}.tool-metrics{gap:14px;flex-wrap:wrap}}
.external-section{margin-top:42px;padding-top:34px;border-top:1px solid #dce2ea}.external-section>header{margin-bottom:20px;display:flex;align-items:flex-end;justify-content:space-between;gap:28px}.external-section>header span{color:var(--brand);font-size:13px;font-weight:800}.external-section>header h2{margin:6px 0 0;color:#172033;font-size:26px}.external-section>header p{max-width:620px;margin:0;color:#657186;font-size:14px;line-height:1.75}.external-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.external-card{min-width:0;padding:22px;border:1px solid #dfe4eb;border-radius:10px;display:flex;flex-direction:column;background:#fff;box-shadow:0 8px 22px rgba(23,32,51,.04)}.external-card-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.external-card-top>small{overflow:hidden;color:#7b8492;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.external-status{flex:none;padding:4px 8px;border-radius:4px;color:#83520a;background:#fff2d0;font-size:12px;font-weight:700}.external-card h3{margin:18px 0 8px;color:#1c2940;font-size:19px}.external-card>p{flex:1;margin:0;color:#586579;font-size:14px;line-height:1.75}.external-tags{margin:18px 0;display:flex;gap:7px;flex-wrap:wrap}.external-tags span{padding:4px 8px;border-radius:4px;color:#506078;background:#f1f4f8;font-size:12px}.external-card footer{padding-top:15px;border-top:1px solid #edf0f4;display:flex;align-items:center;justify-content:space-between;gap:12px}.external-card footer small{color:#818b9a;font-size:11px;line-height:1.5}.external-card footer a{flex:none;display:inline-flex;align-items:center;gap:5px;color:var(--brand);font-size:13px;font-weight:700;text-decoration:none}.external-card footer a:hover{text-decoration:underline}
@media(max-width:1000px){.external-grid{grid-template-columns:1fr 1fr}.external-section>header{align-items:flex-start;flex-direction:column;gap:10px}}
@media(max-width:700px){.external-grid{grid-template-columns:1fr}.external-card footer{align-items:flex-start;flex-direction:column}}
.capability-metrics{display:flex;align-items:center;gap:18px;flex:none}.capability-metrics span{display:inline-flex;align-items:center;gap:6px;color:#667085;font-size:13px}.capability-metrics .el-icon{color:var(--accent);font-size:16px}.capability-metrics b{color:#263349;font-size:15px;font-variant-numeric:tabular-nums}
@media(max-width:700px){.capability-metrics{gap:14px;flex-wrap:wrap}}
</style>
