export const IMA_SHARE_ID = ''
export const IMA_URL = 'https://ima.qq.com/'
export const PUBLIC_DEMO = import.meta.env?.VITE_PUBLIC_DEMO === 'true'
export const TASK_SHEET_URL = ''
export const STAGE_COLORS = ['#2563eb', '#0e7490', '#0f766e', '#15803d', '#65a30d', '#ca8a04', '#ea580c', '#dc2626', '#7c3aed']
export const STAGE_ORDER = ['0-项目管理', '1-项目启动', '2-需求分析', '3-系统设计', '4-编码实现', '5-数据处理', '6-部署测试', '7-运行维护', '8-总结验收']
export const TEAMS = ['演示团队一', '演示团队二', '演示团队三']

export const openImaUrl = (_mediaId?: string | null) => IMA_URL
