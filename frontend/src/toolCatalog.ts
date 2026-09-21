import { capabilityToLegacyTool, validateToolDefinition, type ToolDefinition } from './capabilityManifest'
import { bundledCapabilities } from './migratedCapabilities'

/**
 * 运行页仍使用旧 ToolDefinition 组件接口，本文件是唯一兼容边界。
 * 事实源已经改为 capability.json 2.1，页面不得再直接读取 tools/definitions 下的旧文件。
 */
export const toolProfiles: ToolDefinition[] = bundledCapabilities
  .filter(capability => capability.kind === 'tool' && capability.runtime)
  .map(capabilityToLegacyTool)

for (const tool of toolProfiles) {
  const errors = validateToolDefinition(tool)
  if (errors.length) throw new Error(`工具定义校验失败：${tool.id}：${errors.join('；')}`)
}

export const toolProfileById = new Map(toolProfiles.map(tool => [tool.id, tool]))
export const configurableToolProfiles = toolProfiles.filter(tool => tool.runtime.renderer !== 'custom')
