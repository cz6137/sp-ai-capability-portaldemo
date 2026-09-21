import { defineAsyncComponent, type Component } from 'vue'
import { registeredServerJobHandlerIds, hasRegisteredServerJobHandler } from './registry'

/** 只登记平台已经实现并审查过的服务端任务 UI，不执行 JSON 中的任意脚本。 */
const serverJobRenderers: Record<typeof registeredServerJobHandlerIds[number], Component> = {
  'meeting-minutes-v1': defineAsyncComponent(() => import('../../views/MeetingMinutesToolView.vue')),
}

export function registeredServerJobRenderer(handler?: string) {
  return hasRegisteredServerJobHandler(handler) ? serverJobRenderers[handler as typeof registeredServerJobHandlerIds[number]] : undefined
}
