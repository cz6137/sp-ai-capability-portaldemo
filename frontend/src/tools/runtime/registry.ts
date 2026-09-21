export const registeredToolExecutorIds = [
  'markdown-to-docx',
  'markdown-to-pdf',
  'docx-to-markdown',
  'pdf-to-markdown',
  'image-to-markdown',
] as const

const registered = new Set<string>(registeredToolExecutorIds)

export function hasRegisteredToolExecutor(id: string) {
  return registered.has(id)
}

export const registeredServerJobHandlerIds = ['meeting-minutes-v1'] as const

export function hasRegisteredServerJobHandler(id?: string) {
  return Boolean(id && registeredServerJobHandlerIds.some(value => value === id))
}
