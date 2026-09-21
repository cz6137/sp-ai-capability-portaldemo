<script setup lang="ts">
import { DocumentChecked, EditPen, Files, UploadFilled, CircleCheck, Warning } from '@element-plus/icons-vue'
import PageHero from '../components/PageHero.vue'

const steps = [
  { icon: DocumentChecked, title: '确认场景', text: '从工作台选择明确的交付任务，确认输入、输出、使用对象和成功标准。' },
  { icon: EditPen, title: '设计指令', text: '定义角色、上下文、执行步骤、约束条件和结构化输出格式。' },
  { icon: Files, title: '准备资产', text: '整理模板、示例、参考知识和必要脚本，避免在指令里堆叠大段材料。' },
  { icon: CircleCheck, title: '测试评审', text: '使用典型与边界样例测试，核对准确性、稳定性、可解释性和数据安全。' },
  { icon: UploadFilled, title: '发布迭代', text: '填写版本、负责人和适用范围，发布后收集真实交付反馈并持续迭代。' },
]
</script>

<template>
  <PageHero eyebrow="SKILL BUILDING GUIDE" title="Skill 构建指南" description="把个人经验转化为团队可复用的 AI 交付能力，从场景定义到发布迭代形成统一方法。" />
  <section class="section"><div class="container guide-layout">
    <aside class="guide-nav surface"><b>指南目录</b><a href="#process">构建流程</a><a href="#structure">标准结构</a><a href="#quality">质量检查</a><a href="#publish">发布规范</a><RouterLink to="/norms">进入 Skill 工作台</RouterLink></aside>
    <div class="guide-content">
      <section id="process"><div class="section-heading"><div><h2>构建流程</h2><p>每个 Skill 都应从真实交付场景出发。</p></div></div><div class="steps"><article v-for="(step,index) in steps" :key="step.title"><span>{{ index + 1 }}</span><el-icon><component :is="step.icon" /></el-icon><div><h3>{{ step.title }}</h3><p>{{ step.text }}</p></div></article></div></section>
      <section id="structure" class="guide-section"><h2>标准结构</h2><div class="structure-grid"><article><b>SKILL.md</b><p>描述能力目标、适用场景、输入输出、执行步骤与边界条件。</p></article><article><b>references/</b><p>存放规范、术语、业务知识和少量高价值参考资料。</p></article><article><b>scripts/</b><p>存放需要确定性执行的数据处理、校验或格式转换脚本。</p></article><article><b>assets/</b><p>存放模板、示例文件和最终产出需要复用的静态资产。</p></article></div></section>
      <section id="quality" class="guide-section"><h2>质量检查</h2><el-alert title="禁止在 Skill 中写入账号、密钥、客户敏感信息或未经授权的内部接口凭据。" type="warning" :icon="Warning" show-icon :closable="false"/><div class="check-grid"><label v-for="item in ['目标和使用场景清晰','输入输出格式明确','异常和边界条件完整','示例可独立复现','引用资产路径有效','版本和负责人已登记']" :key="item"><el-checkbox :model-value="false"/>{{ item }}</label></div></section>
      <section id="publish" class="guide-section"><h2>发布规范</h2><p>发布前完成同组评审，在工作台更新对应任务状态；变更应记录版本、更新内容、兼容性和验证结果。Skill 名称使用小写英文与短横线，保持语义明确，例如 <code>delivery-weekly-report</code>。</p></section>
    </div>
  </div></section>
</template>

<style scoped>
.guide-layout { display: grid; grid-template-columns: 220px minmax(0,1fr); gap: 34px; align-items: start; }.guide-nav { position: sticky; top: 88px; padding: 12px; display: flex; flex-direction: column; }.guide-nav b { padding: 9px 10px; }.guide-nav a { padding: 10px; color: var(--muted); font-size: 13px; border-radius: 5px; }.guide-nav a:hover { color: var(--brand); background: var(--brand-soft); }.guide-nav a:last-child { margin-top: 10px; color: white; background: var(--brand); }.steps { display: flex; flex-direction: column; }.steps article { display: grid; grid-template-columns: 32px 42px 1fr; gap: 14px; position: relative; padding: 0 0 26px; }.steps article > span { width: 28px; height: 28px; display: grid; place-items:center; color:white; background:var(--brand); border-radius:50%; font-size:12px; font-weight:800; }.steps article:not(:last-child)::after { content:''; position:absolute; left:13px; top:30px; bottom:2px; width:1px; background:#d5dbe5; }.steps article > .el-icon { width:42px;height:42px;display:grid;place-items:center;background:#eef2f7;border-radius:6px;color:#334155;font-size:20px}.steps h3 { margin:1px 0 5px;font-size:16px}.steps p,.guide-section>p{margin:0;color:var(--muted);line-height:1.8;font-size:13px}.guide-section{padding-top:42px;scroll-margin-top:80px}.guide-section h2{font-size:22px}.structure-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.structure-grid article{padding:18px;background:white;border:1px solid var(--line);border-radius:6px}.structure-grid b{color:var(--brand);font-family:monospace}.structure-grid p{color:var(--muted);font-size:13px;line-height:1.7}.check-grid{margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:10px}.check-grid label{padding:12px;background:white;border:1px solid var(--line);border-radius:5px;font-size:13px}code{padding:2px 5px;background:#e8ebf0;border-radius:3px}
@media(max-width:750px){.guide-layout{grid-template-columns:1fr}.guide-nav{position:static;display:grid;grid-template-columns:1fr 1fr}.guide-nav b{grid-column:1/3}.structure-grid,.check-grid{grid-template-columns:1fr}}
</style>
