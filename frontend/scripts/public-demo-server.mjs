import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs'
import http from 'node:http'
import { dirname, extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { meetingMinutesCapabilities, meetingMinutesDemoJob, startMeetingMinutesDemoJob } from './meeting-minutes-demo.mjs'

const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
const distDirectory = resolve(scriptsDirectory, '..', 'dist')
const host = process.env.PORTAL_DEMO_HOST || process.env.HOST || '0.0.0.0'
const port = Number(process.env.PORTAL_DEMO_PORT || process.env.PORT || 8080)
const identity = {
  id: 'public-demo-admin',
  username: 'demo',
  displayName: '管理员',
  roles: [{ authority: 'ROLE_ADMIN' }],
  accessToken: 'public-demo',
}

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.wasm': 'application/wasm',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.zip': 'application/octet-stream',
}

function envelope(response, status, data, message = '公开演示接口') {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  })
  response.end(JSON.stringify({ code: status === 200 ? 'OK' : 'DEMO_ONLY', message, data, traceId: 'public-demo' }))
}

async function handleApi(request, response, pathname) {
  const apiPath = pathname.slice('/sp-ai-portal/api/v1'.length) || '/'
  if (['/auth/me', '/auth/login', '/auth/refresh'].includes(apiPath)) return envelope(response, 200, identity)
  if (apiPath === '/auth/logout') return envelope(response, 200, null)
  if (request.method === 'GET' && ['/stages', '/assets', '/case-categories'].includes(apiPath)) return envelope(response, 200, [])
  if (request.method === 'GET' && apiPath === '/tools/meeting-minutes/capabilities') return envelope(response, 200, meetingMinutesCapabilities())
  if (request.method === 'POST' && apiPath === '/tools/meeting-minutes/jobs') {
    try { return envelope(response, 200, await startMeetingMinutesDemoJob(request), '演示任务已创建') }
    catch (error) { return envelope(response, 400, null, error?.message || '创建演示任务失败') }
  }
  const jobMatch = request.method === 'GET' && apiPath.match(/^\/tools\/meeting-minutes\/jobs\/([^/]+)$/)
  if (jobMatch) {
    try { return envelope(response, 200, meetingMinutesDemoJob(decodeURIComponent(jobMatch[1]))) }
    catch (error) { return envelope(response, 404, null, error?.message || '演示任务不存在') }
  }
  request.resume()
  return envelope(response, 503, null, '此操作在公开 Demo 中使用浏览器演示数据，不写入正式数据库。')
}

function safeStaticPath(pathname) {
  const prefix = '/sp-ai-portal-web/'
  if (!pathname.startsWith(prefix)) return null
  let relative
  try { relative = decodeURIComponent(pathname.slice(prefix.length)) }
  catch { return null }
  if (!relative || relative.endsWith('/')) relative += 'index.html'
  const candidate = resolve(distDirectory, relative)
  return candidate === distDirectory || candidate.startsWith(`${distDirectory}${sep}`) ? candidate : null
}

function sendFile(request, response, file) {
  const details = statSync(file)
  response.writeHead(200, {
    'Content-Type': contentTypes[extname(file).toLowerCase()] || 'application/octet-stream',
    'Content-Length': details.size,
    'Cache-Control': extname(file) === '.html' ? 'no-store' : 'public, max-age=3600',
    'X-Content-Type-Options': 'nosniff',
  })
  if (request.method === 'HEAD') return response.end()
  createReadStream(file).pipe(response)
}

if (!existsSync(resolve(distDirectory, 'index.html'))) {
  console.error('缺少 frontend/dist，请先运行 pnpm build。')
  process.exit(1)
}

const server = http.createServer(async (request, response) => {
  const pathname = new URL(request.url || '/', 'http://127.0.0.1').pathname
  if (pathname.startsWith('/sp-ai-portal/api/v1')) return handleApi(request, response, pathname)
  if (pathname === '/healthz') return envelope(response, 200, { status: 'UP', mode: 'PUBLIC_DEMO' })
  if (pathname === '/' || pathname === '/sp-ai-portal-web') {
    response.writeHead(302, { Location: '/sp-ai-portal-web/' })
    return response.end()
  }
  if (!['GET', 'HEAD'].includes(request.method || 'GET')) {
    response.writeHead(405)
    return response.end()
  }
  const file = safeStaticPath(pathname)
  if (file && existsSync(file) && statSync(file).isFile()) return sendFile(request, response, file)
  if (pathname.startsWith('/sp-ai-portal-web/')) return sendFile(request, response, resolve(distDirectory, 'index.html'))
  response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
  response.end('Not found')
})

server.listen(port, host, () => {
  console.log(`公开 Demo 已启动：http://${host}:${port}/sp-ai-portal-web/`)
})
