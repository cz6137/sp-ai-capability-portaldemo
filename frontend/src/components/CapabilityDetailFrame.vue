<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Box, CircleCheck, Download, Tools } from '@element-plus/icons-vue'
import type { CapabilityManifestV2 } from '../capabilitySchema'
import type { CapabilitySelection } from '../capabilityAccess'
import CapabilityDownloadButton from './CapabilityDownloadButton.vue'

const props = defineProps<{ manifest: CapabilityManifestV2; preview?: boolean; selection?: CapabilitySelection; runnable?: boolean; experienceOnly?: boolean; backLabel?: string }>()
const emit = defineEmits<{ run: []; back: [] }>()
const kindLabel = computed(() => props.manifest.kind === 'skill' ? 'Skill 能力包' : '业务工具')
const statusLabel = computed(() => ({ DRAFT: '草稿', IN_REVIEW: '待审核', PUBLISHED: '已发布', ARCHIVED: '已归档' }[props.manifest.governance.status]))
const warnings = computed(() => props.manifest.quality.warnings || [])
const activeSection = ref('overview')
const anchorPrefix = computed(() => `capability-${props.manifest.identity.slug || 'draft'}`)
const sectionId = (name: string) => `${anchorPrefix.value}-${name}`
const sectionNames = ['overview', 'use-cases', 'inputs', 'process', 'outputs', 'technical']
let sectionObserver: IntersectionObserver | undefined
const navigateTo = (name: string) => {
  activeSection.value = name
  const target = document.getElementById(sectionId(name))
  if (name === 'technical' && target instanceof HTMLDetailsElement) target.open = true
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
onMounted(async () => {
  await nextTick()
  sectionObserver = new IntersectionObserver((entries) => {
    const visible = entries.filter(entry => entry.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
    const current = visible[0]?.target.id.split(`${anchorPrefix.value}-`)[1]
    if (current) activeSection.value = current
  }, { rootMargin: '-18% 0px -68% 0px', threshold: 0 })
  sectionNames.map(sectionId).map(id => document.getElementById(id)).filter(Boolean).forEach(element => sectionObserver?.observe(element!))
})
onBeforeUnmount(() => sectionObserver?.disconnect())
const referenceEntries = computed(() => [
  { label: '使用手册', value: props.manifest.references?.manual },
  { label: '常见问题', value: props.manifest.references?.faq },
  { label: '版本记录', value: props.manifest.references?.changelog },
].filter(item => item.value))
const provenanceLabel = (path: string) => {
  const record = props.manifest.provenance?.find(item => item.path === path || path.startsWith(`${item.path}/`))
  return record?.status === 'EXTRACTED' ? '包内提取' : record?.status === 'USER_CONFIRMED' ? '用户确认' : record?.status === 'USER_EDITED' ? '用户修改' : '未确认'
}
</script>

<template>
  <div class="capability-layout" :style="{ '--asset-accent': manifest.identity.accent || '#ad2d2d' }">
    <aside class="detail-nav" aria-label="能力详情导航">
      <button v-if="backLabel" class="detail-back" type="button" @click="emit('back')">← {{ backLabel }}</button>
      <b>内容导航</b>
      <nav>
        <a :class="{ active: activeSection === 'overview' }" :href="`#${sectionId('overview')}`" @click.prevent="navigateTo('overview')"><span>00</span>能力概览</a>
        <a :class="{ active: activeSection === 'use-cases' }" :href="`#${sectionId('use-cases')}`" @click.prevent="navigateTo('use-cases')"><span>01</span>适用任务</a>
        <a :class="{ active: activeSection === 'inputs' }" :href="`#${sectionId('inputs')}`" @click.prevent="navigateTo('inputs')"><span>02</span>输入资料</a>
        <a :class="{ active: activeSection === 'process' }" :href="`#${sectionId('process')}`" @click.prevent="navigateTo('process')"><span>03</span>处理流程</a>
        <a :class="{ active: activeSection === 'outputs' }" :href="`#${sectionId('outputs')}`" @click.prevent="navigateTo('outputs')"><span>04</span>输出成果</a>
        <a :class="{ active: activeSection === 'technical' }" :href="`#${sectionId('technical')}`" @click.prevent="navigateTo('technical')"><span>05</span>技术与交付</a>
      </nav>
    </aside>

    <article class="capability-frame">
    <header :id="sectionId('overview')" class="asset-hero">
      <div class="kind-mark"><el-icon><component :is="manifest.kind === 'skill' ? Box : Tools" /></el-icon>{{ kindLabel }}</div>
      <div class="hero-copy">
        <div class="hero-meta"><span>{{ preview ? 'DEMO' : statusLabel }}</span><span>v{{ manifest.identity.version }}</span></div>
        <h1>{{ manifest.identity.name || '未命名能力' }}</h1>
        <p class="tagline">{{ manifest.identity.tagline || '尚未填写一句话说明' }}</p>
        <p>{{ manifest.identity.description || '尚未填写完整能力说明。' }}</p>
        <div class="hero-actions">
          <CapabilityDownloadButton v-if="selection && manifest.delivery.package" :selected="selection" primary />
          <el-button v-if="runnable" type="primary" size="large" :icon="Tools" @click="emit('run')">进入使用</el-button>
          <span v-if="runnable && experienceOnly">可查看流程，处理服务尚未接通</span>
        </div>
      </div>
      <dl>
        <div><dt>适用对象</dt><dd>{{ manifest.classification.audiences.join('、') || '待确认' }}</dd></div>
        <div><dt>维护人 <small>{{ provenanceLabel('/identity/maintainer') }}</small></dt><dd>{{ manifest.identity.maintainer || '待指定' }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ manifest.identity.updatedAt }}</dd></div>
      </dl>
    </header>

    <main class="answer-sections">
      <section :id="sectionId('use-cases')" class="use-case-section">
        <div class="question-heading"><span>01</span><div><h2>适用任务</h2><p>以下任务可直接使用本能力处理。</p></div></div>
        <ul class="scenario-list"><li v-for="item in manifest.usage.scenarios" :key="item">{{ item }}</li></ul>
      </section>

      <section :id="sectionId('inputs')" class="input-section">
        <div class="question-heading"><span>02</span><div><h2>输入资料</h2><p>提交原始文件或目录，无需预先转换为平台清单字段。</p></div></div>
        <div class="material-grid"><article v-for="item in manifest.usage.inputs" :key="item.name"><b>{{ item.name }}</b><p v-if="item.detail">{{ item.detail }}</p><small v-if="item.formats?.length">可读取：{{ item.formats.join(' / ') }}</small></article></div>
      </section>

      <section :id="sectionId('process')" class="process-section">
        <div class="question-heading"><span>03</span><div><h2>处理流程</h2><p>系统先读取能力入口及引用规则，再按既定步骤处理资料。</p></div></div>
        <ol class="workflow"><li v-for="(step,index) in manifest.usage.workflow" :key="index"><span>{{ index + 1 }}</span><div><b>{{ step.title }}</b><p>{{ step.detail }}</p></div></li></ol>
        <div v-if="manifest.quality.humanReview.length" class="human-review"><h3>需要人工确认的节点</h3><ul><li v-for="item in manifest.quality.humanReview" :key="item">{{ item }}</li></ul></div>
      </section>

      <section :id="sectionId('outputs')" class="output-section">
        <div class="question-heading"><span>04</span><div><h2>输出成果</h2><p>系统输出以下成果，并为判断结果保留相应依据。</p></div></div>
        <div class="output-grid"><article v-for="item in manifest.usage.outputs" :key="item.name"><el-icon><CircleCheck /></el-icon><div><b>{{ item.name }}</b><p v-if="item.detail">{{ item.detail }}</p><small v-if="item.formats?.length">{{ item.formats.join(' / ') }}</small></div></article></div>
        <h3 class="evidence-title">判断依据</h3>
        <div class="quality-grid"><article v-for="item in manifest.quality.dimensions" :key="item.name"><b>{{ item.name }}</b><p>{{ item.criteria }}</p><small>依据：{{ item.evidence }}</small></article></div>
      </section>
    </main>

    <details :id="sectionId('technical')" class="technical-details">
      <summary>技术与交付信息</summary>
      <div class="technical-grid">
        <section v-if="warnings.length || manifest.quality.boundaries.length"><h3>机器执行约束</h3><p>系统执行时读取以下约束；维护人员可依据来源信息进行审计。</p><ul class="machine-rules"><li v-for="(warning,index) in warnings" :key="`${warning.source.file}-${warning.source.line}-${index}`"><b>{{ warning.text }}</b><small>{{ warning.source.file }}<template v-if="warning.source.line"> 第 {{ warning.source.line }} 行</template> · {{ provenanceLabel(`/quality/warnings/${index}`) }}</small></li><li v-for="item in manifest.quality.boundaries" :key="item">{{ item }}</li></ul></section>
        <section v-if="manifest.delivery.package"><h3><el-icon><Download /></el-icon>能力包</h3><p>{{ manifest.delivery.package.environment }}</p><p>{{ manifest.delivery.package.installGuide }}</p><ul><li v-for="item in manifest.delivery.package.packageItems" :key="item.name"><b>{{ item.name }}</b><span v-if="item.detail"> — {{ item.detail }}</span></li></ul></section>
        <section v-if="manifest.delivery.online"><h3><el-icon><Tools /></el-icon>在线运行</h3><p>{{ manifest.delivery.online.enabled ? '已开放' : '暂未开放' }} · 文件上限 {{ manifest.delivery.online.maxFileSizeMB }} MB</p><p>{{ manifest.delivery.online.processingLocation }}</p><p>{{ manifest.delivery.online.dataNotice }}</p></section>
        <section><h3>维护与资料</h3><p>版本说明：{{ manifest.governance.changeNote || '未填写' }}</p><p>审核人：{{ manifest.governance.reviewer || '尚未审核' }}</p><dl v-if="referenceEntries.length"><div v-for="item in referenceEntries" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl></section>
      </div>
    </details>
    </article>
  </div>
</template>

<style scoped>
.capability-layout{--asset-accent:#ad2d2d;width:100%;max-width:1390px;margin:0 auto;display:grid;grid-template-columns:184px minmax(0,1fr);gap:18px;align-items:start}.capability-frame{min-width:0;color:#243247;background:#f4f6f8;border:1px solid #dce2e8;border-radius:16px;overflow:hidden}.detail-nav{position:sticky;top:94px;padding:14px 12px 20px;background:#fff;border:1px solid #dce2e8;border-radius:12px;box-shadow:0 8px 24px rgba(31,45,61,.06)}.detail-back{width:100%;min-height:42px;margin-bottom:15px;padding:8px 10px;border:1px solid var(--asset-accent);border-radius:7px;color:var(--asset-accent);background:#fff;font-size:13px;font-weight:800;cursor:pointer;transition:color .18s ease,background-color .18s ease,box-shadow .18s ease}.detail-back:hover{color:#fff;background:var(--asset-accent);box-shadow:0 5px 14px color-mix(in srgb,var(--asset-accent) 25%,transparent)}.detail-back:focus-visible{outline:3px solid color-mix(in srgb,var(--asset-accent) 32%,transparent);outline-offset:2px}.detail-nav>b{display:block;padding:0 10px 13px;color:#172235;font-size:14px;border-bottom:1px solid #e8ecf0}.detail-nav nav{display:grid;padding-top:9px}.detail-nav a{padding:11px 10px;display:flex;align-items:center;gap:10px;color:#617085;border-left:3px solid transparent;text-decoration:none;font-size:14px;line-height:1.4}.detail-nav a span{width:24px;color:#9aa5b2;font-size:11px;font-weight:800}.detail-nav a:hover{color:#25364b;background:#f6f8fa}.detail-nav a.active{color:var(--asset-accent);border-left-color:var(--asset-accent);background:#faf3f3;font-weight:800}.detail-nav a.active span{color:var(--asset-accent)}.asset-hero{display:grid;grid-template-columns:150px minmax(0,1fr) 255px;background:#fff;border-top:5px solid var(--asset-accent);scroll-margin-top:94px}.kind-mark{padding:34px 24px;display:flex;flex-direction:column;align-items:center;gap:12px;color:var(--asset-accent);font-weight:800;border-right:1px solid #e2e6eb}.kind-mark .el-icon{font-size:36px}.hero-copy{padding:34px 40px}.hero-meta{display:flex;gap:10px;margin-bottom:12px;color:#67768a;font-size:13px}.hero-meta span{padding:4px 9px;background:#f1f3f6;border-radius:999px}.hero-copy h1{margin:0;font-size:34px;color:#172235}.hero-copy>p{max-width:760px;line-height:1.8}.tagline{font-size:19px;font-weight:700;color:#394b62}.hero-actions{display:flex;align-items:center;gap:14px;margin-top:22px}.hero-actions span{font-size:13px;color:#7a8798}.asset-hero>dl{margin:0;padding:30px 24px;border-left:1px solid #e2e6eb}.asset-hero>dl div{padding:0 0 18px;margin-bottom:18px;border-bottom:1px solid #edf0f3}.asset-hero dt{font-size:13px;color:#718095}.asset-hero dt small{margin-left:6px;color:#9a6a22}.asset-hero dd{margin:7px 0 0;font-weight:700;line-height:1.5}.answer-sections{display:grid;gap:1px;background:#dfe4e9;border-top:1px solid #dfe4e9;border-bottom:1px solid #dfe4e9}.answer-sections>section{padding:38px 46px;background:#fff;scroll-margin-top:94px}.question-heading{display:flex;gap:18px;align-items:flex-start;margin-bottom:26px}.question-heading>span{font-size:13px;font-weight:800;color:#fff;background:var(--asset-accent);padding:7px 9px;border-radius:7px}.question-heading h2{margin:0;font-size:25px}.question-heading p{margin:6px 0 0;color:#68778a}.scenario-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:12px;margin:0;padding:0;list-style:none}.scenario-list li{padding:17px 19px;border-left:4px solid var(--asset-accent);background:#f8fafc;line-height:1.7}.material-grid,.output-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px}.material-grid article,.output-grid article{padding:20px;border:1px solid #dfe4e9;border-radius:10px;background:#f8fafc}.material-grid p,.output-grid p{margin:7px 0;color:#56677c;line-height:1.7}.material-grid small,.output-grid small{color:#718095}.output-grid article{display:flex;gap:11px;background:#f5faf7}.output-grid .el-icon{flex:0 0 auto;color:#3c8a5c;margin-top:3px}.workflow{display:grid;gap:12px;padding:0;list-style:none}.workflow li{display:flex;gap:14px;padding:16px 18px;background:#f8fafc;border-radius:10px}.workflow li>span{flex:0 0 28px;height:28px;display:grid;place-items:center;border-radius:50%;background:#e9edf2;font-weight:800}.workflow p{margin:5px 0;color:#617085;line-height:1.7}.evidence-title{margin:28px 0 14px}.quality-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.quality-grid article{padding:19px;border:1px solid #dfe4e9;border-radius:10px}.quality-grid p{line-height:1.7}.quality-grid small{display:block;color:#657589;line-height:1.6}.human-review{margin-top:22px;padding:20px 24px;background:#fff8eb;border-left:4px solid #d8831f}.human-review h3{margin-top:0}.human-review ul{margin-bottom:0}.technical-details{margin:22px;border:1px solid #dce2e8;border-radius:12px;background:#fff;scroll-margin-top:94px}.technical-details>summary{padding:19px 22px;cursor:pointer;font-weight:800;color:#43546a}.technical-details[open]>summary{border-bottom:1px solid #e2e6eb}.technical-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:24px;padding:24px}.technical-grid section{min-width:0}.technical-grid h3{display:flex;align-items:center;gap:8px}.technical-grid p,.technical-grid li{color:#526278;line-height:1.7;overflow-wrap:anywhere}.technical-grid ul{padding-left:20px}.technical-grid dl div{display:grid;grid-template-columns:80px 1fr;gap:10px;padding:7px 0}.technical-grid dt{font-weight:700}.technical-grid dd{margin:0;color:#5e6e82;overflow-wrap:anywhere}.machine-rules{display:grid;gap:10px;max-height:320px;overflow:auto}.machine-rules li{display:flex;flex-direction:column;gap:3px}.machine-rules small{color:#806f58}
@media(max-width:1100px){.capability-layout{display:block}.detail-nav{position:static;margin-bottom:14px;padding:10px}.detail-nav>b,.detail-nav nav{display:none}.detail-back{width:auto;margin:0}}
@media(max-width:900px){.asset-hero{grid-template-columns:110px 1fr}.asset-hero>dl{grid-column:1/-1;display:grid;grid-template-columns:repeat(3,1fr);border-left:0;border-top:1px solid #e2e6eb}.two-columns,.io-grid{grid-template-columns:1fr}.quality-grid{grid-template-columns:1fr 1fr}}
@media(max-width:640px){.asset-hero{display:block}.kind-mark{flex-direction:row;justify-content:flex-start;padding:18px;border-right:0;border-bottom:1px solid #e2e6eb}.hero-copy{padding:26px 20px}.hero-copy h1{font-size:28px}.asset-hero>dl{grid-template-columns:1fr;padding:20px}.answer-sections>section{padding:30px 20px}.warning-panel{margin:14px}.quick-start,.quality-grid{grid-template-columns:1fr}.technical-details{margin:14px}.technical-grid{padding:20px}}

/* 四段内容使用同一组件和数据合同，仅以低饱和色区分阅读任务。 */
.asset-hero{background:linear-gradient(120deg,#fff 0%,#fff 68%,#f7f3f3 100%)}
.answer-sections>section{position:relative}
.use-case-section{--section-accent:#3f648b;background:linear-gradient(135deg,#fff 0%,#f4f8fc 100%)!important}
.input-section{--section-accent:#9a6728;background:linear-gradient(135deg,#fff 0%,#fcf8f1 100%)!important}
.process-section{--section-accent:#5b568e;background:linear-gradient(135deg,#fff 0%,#f6f5fb 100%)!important}
.output-section{--section-accent:#39725b;background:linear-gradient(135deg,#fff 0%,#f2f8f5 100%)!important}
.answer-sections>section .question-heading>span{background:var(--section-accent)}
.use-case-section .scenario-list li{border-left-color:#6d8eae;background:#eef5fb}
.input-section .material-grid article{border-color:#eadbc3;background:#fffaf2}
.process-section .workflow li{border:1px solid #e0dded;background:#f9f8fd}
.process-section .workflow li>span{color:#fff;background:#7770a5}
.output-section .output-grid article{border-color:#cfe1d7;background:#f4faf7}
.output-section .quality-grid article{border-color:#dbe8e1;background:rgba(255,255,255,.72)}
</style>
