import http from 'node:http'

const stages = ['项目监控','预投立项','需求分析','系统设计','编码实现','数据处理','部署测试','运行维护','总结验收'].map((name, stage) => ({ stage, name: `${stage}-${name}`, description: `${name}阶段交付资产`, counts: { sd_template: 6 + stage, case: 2 + stage, ai_tool: stage % 3 + 1 }, subcategories: [] }))
const skills = [
  { id: 'skill-1', slug: 'delivery-weekly-report', name: '项目周报生成器', description: '自动汇总项目进度、风险和计划，生成标准项目周报。', status: 'PUBLISHED', downloadCount: 356, sourceType: 'INTERNAL', caseText: '已用于华南交付团队周例会，周报整理时间明显缩短。', usageGuide: '上传项目进展、风险问题和下周计划。\n确认统计周期后生成周报并人工复核。', enabled: true, stageIds: [0], createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-18T00:00:00Z' },
  { id: 'skill-2', slug: 'delivery-requirement-parser', name: '需求文档解析', description: '解析需求材料并生成结构化需求追踪矩阵。', status: 'PUBLISHED', downloadCount: 192, sourceType: 'INTERNAL', caseText: '用于需求调研材料归集。', usageGuide: '上传需求文档并选择业务域。', enabled: true, stageIds: [2], createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-18T00:00:00Z' },
  { id: 'skill-3', slug: 'delivery-script-security-review', name: '脚本安全审核', description: '识别 SQL 与 Shell 脚本中的高风险语句和权限问题。', status: 'IN_REVIEW', downloadCount: 143, sourceType: 'INTERNAL', caseText: '用于上线变更审核。', usageGuide: '上传脚本包并选择运行环境。', enabled: true, stageIds: [4,6], createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-18T00:00:00Z' },
]
const cases = [
  { id: 'case-1', name: '广州市不动产登记项目交付总结.docx', type: 'C', stage_num: 8, stage_name: '8-总结验收', sub_name: '项目工作总结报告', sub_number: '20', media_id: 'media-1', case_category_id: 'cat-1' },
  { id: 'case-2', name: '湛江市不动产登记平台系统设计说明书.docx', type: 'C', stage_num: 3, stage_name: '3-系统设计', sub_name: '系统设计说明书', sub_number: '05', media_id: 'media-2', case_category_id: 'cat-1' },
]
const categories = [
  { id: 'cat-1', code: 'real-estate', name: '不动产管理', description: '不动产登记、房产交易、确权登记等项目的完整交付材料。', count: 2, examples: cases.map(item => item.name) },
  { id: 'cat-2', code: 'spatial-platform', name: '时空数据与一张图', description: '时空大数据平台、GIS 平台与城市大脑项目材料。', count: 8, examples: ['智慧城市时空平台总体设计方案.pdf'] },
  { id: 'cat-3', code: 'natural-resources', name: '自然资源一体化', description: '自然资源治理、用途管制与国土空间规划项目材料。', count: 12, examples: ['自然资源一体化政务服务实施方案.docx'] },
]
const envelope = data => JSON.stringify({ code: 'OK', message: '成功', data, traceId: 'visual-qa' })

http.createServer((request, response) => {
  response.setHeader('Content-Type', 'application/json;charset=UTF-8')
  const path = new URL(request.url, 'http://localhost').pathname.replace('/sp-ai-portal/api/v1', '')
  if (path === '/auth/me' || path === '/auth/refresh') return response.end(envelope({ id: 'admin', username: 'admin', displayName: '平台管理员', roles: [{ authority: 'ROLE_ADMIN' }], accessToken: 'visual-token' }))
  if (path === '/stages') return response.end(envelope(stages))
  if (path === '/assets') return response.end(envelope(cases))
  if (path === '/skills') return response.end(envelope(skills.filter(item => item.status === 'PUBLISHED')))
  if (path === '/skills/manage') return response.end(envelope(skills.map((skill, index) => ({ skill, latestVersion: { id: `v${index}`, skillId: skill.id, versionName: '1.0.0', status: skill.status, createdAt: '2026-08-18T00:00:00Z' } }))))
  if (path.startsWith('/skills/')) { const skill = skills.find(item => item.id === path.split('/')[2]) || skills[0]; return response.end(envelope({ skill, versions: [{ id: 'v1', skillId: skill.id, versionName: '1.0.0', status: 'PUBLISHED', changeNote: '首次发布', createdAt: '2026-08-18T00:00:00Z' }], downloadAvailable: true })) }
  if (path === '/case-categories') return response.end(envelope(categories))
  if (path.startsWith('/case-categories/')) return response.end(envelope({ category: categories.find(item => item.code === path.split('/')[2]) || categories[0], items: cases }))
  response.statusCode = 404
  response.end(envelope(null))
}).listen(8080, '127.0.0.1', () => console.log('Visual mock API: http://127.0.0.1:8080'))
