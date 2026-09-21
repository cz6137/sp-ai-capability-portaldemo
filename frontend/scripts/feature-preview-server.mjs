import http from 'node:http'
import { meetingMinutesCapabilities, meetingMinutesDemoJob, startMeetingMinutesDemoJob } from './meeting-minutes-demo.mjs'

// 为无数据库的本地页面提供身份与基础数据。
const identity = { id: 'local-admin', username: 'admin', displayName: '管理员', roles: [{ authority: 'ROLE_ADMIN' }], accessToken: 'local-preview-only' }
const server = http.createServer(async (request, response) => {
  response.setHeader('Content-Type', 'application/json;charset=utf-8')
  response.setHeader('Cache-Control', 'no-store')
  const pathname = new URL(request.url, 'http://127.0.0.1').pathname.replace('/sp-ai-portal/api/v1', '')
  function send(status, data, message = '本地辅助接口') {
    response.writeHead(status)
    response.end(JSON.stringify({ code: status === 200 ? 'OK' : 'PREVIEW_ONLY', message, data, traceId: 'local-preview' }))
  }
  if (['/auth/me', '/auth/login', '/auth/refresh'].includes(pathname)) return send(200, identity)
  if (pathname === '/auth/logout') return send(200, null)
  if (request.method === 'GET' && ['/stages', '/assets', '/case-categories'].includes(pathname)) return send(200, [])
  if (request.method === 'GET' && pathname === '/tools/meeting-minutes/capabilities') return send(200, meetingMinutesCapabilities())
  if (request.method === 'POST' && pathname === '/tools/meeting-minutes/jobs') {
    try { return send(200, await startMeetingMinutesDemoJob(request), '演示任务已创建') }
    catch (error) { return send(400, null, error?.message || '创建演示任务失败') }
  }
  const jobMatch = request.method === 'GET' && pathname.match(/^\/tools\/meeting-minutes\/jobs\/([^/]+)$/)
  if (jobMatch) {
    try { return send(200, meetingMinutesDemoJob(decodeURIComponent(jobMatch[1]))) }
    catch (error) { return send(404, null, error?.message || '演示任务不存在') }
  }
  request.resume()
  return send(503, null, '此操作需要接入业务服务。')
})

server.listen(8080, '127.0.0.1', () => console.log('本地辅助接口：http://127.0.0.1:8080'))
