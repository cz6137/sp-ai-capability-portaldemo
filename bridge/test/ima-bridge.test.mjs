import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { assetType, collectImaMetadata, displayName, isFolder, isImaRateLimit, normalizePortalApi, stageNumber, stripNumber, synchronize } from '../src/ima-bridge.mjs'

test('recognizes all supported stage folder naming conventions', () => {
  assert.equal(stageNumber('0-项目监控'), 0)
  assert.equal(stageNumber('1 项目启动'), 1)
  assert.equal(stageNumber('需求分析'), 2)
  assert.equal(stageNumber('8_总结验收'), 8)
})

test('converts IMA list metadata into baseline naming fields', () => {
  assert.equal(displayName('0 - T - 01 - 项目周报模板.docx'), '项目周报模板.docx')
  assert.equal(assetType('2 - A - 03 - requirement-skill'), 'A')
  assert.equal(assetType('8 - C - 01 - 案例.docx'), 'C')
  assert.equal(stripNumber('01-项目周报'), '项目周报')
  assert.equal(isFolder({ media_type: '99' }), true)
})

test('normalizes the intranet application context to the API prefix', () => {
  assert.equal(normalizePortalApi('http://host:8080/sp-ai-portal/'), 'http://host:8080/sp-ai-portal/api/v1')
  assert.equal(normalizePortalApi('http://host/api/v1'), 'http://host/api/v1')
})

test('recognizes an application-level IMA rate-limit response', () => {
  assert.equal(isImaRateLimit({ code: 10013, msg: '请求频率超限，请稍后重试' }), true)
  assert.equal(isImaRateLimit({ code: 0, data: {} }), false)
})

test('reads paginated IMA roots and recursively maps files', async (context) => {
  const requests = []
  let limited = false
  const server = http.createServer((request, response) => {
    let raw = ''
    request.on('data', chunk => { raw += chunk })
    request.on('end', () => {
      const body = JSON.parse(raw)
      requests.push({ body, clientId: request.headers['ima-openapi-clientid'] })
      if (!limited) {
        limited = true
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ code: 10013, msg: '请求频率超限，请稍后重试' }))
        return
      }
      let data
      if (!body.folder_id && !body.cursor) {
        data = { knowledge_list: [{ media_id: 'ignored', title: '其他目录', media_type: 99 }], is_end: false, next_cursor: 'page-2' }
      } else if (!body.folder_id) {
        data = { knowledge_list: [{ media_id: 'stage-0', title: '0-项目监控', media_type: 99 }], is_end: true }
      } else if (body.folder_id === 'stage-0') {
        data = { knowledge_list: [{ media_id: 'category-1', title: '01-项目周报', media_type: 99 }], is_end: true }
      } else if (body.folder_id === 'category-1') {
        data = { knowledge_list: [{ media_id: 'nested', title: '模板', media_type: 99 }], is_end: true }
      } else {
        data = { knowledge_list: [{ media_id: 'media-1', title: '0 - T - 01 - 项目周报.docx', media_type: 1 }], is_end: true }
      }
      response.writeHead(200, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ code: 0, data }))
    })
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  context.after(() => server.close())
  const cfg = {
    imaBase: `http://127.0.0.1:${server.address().port}`,
    clientId: 'client', apiKey: 'key', knowledgeBaseId: 'knowledge', rootFolderId: null,
    retries: 0, timeout: 2000, imaRequestInterval: 0, imaStagePause: 0,
    imaRateLimitRetries: 2, imaRateLimitBackoff: 1,
  }

  const snapshot = await collectImaMetadata(cfg)
  assert.deepEqual(snapshot.discoveredStages, [0])
  assert.equal(snapshot.items.length, 1)
  assert.equal(snapshot.items[0].mediaId, 'media-1')
  assert.equal(snapshot.items[0].categoryName, '项目周报')
  assert.equal(requests.length, 6)
  assert.ok(requests.every(entry => entry.clientId === 'client'))
})

test('streams IMA items to the portal in bounded batches', async (context) => {
  const ima = http.createServer((request, response) => {
    if (request.method === 'GET' && request.url.startsWith('/download/')) {
      response.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': '3' })
      response.end('abc')
      return
    }
    let raw = ''
    request.on('data', chunk => { raw += chunk })
    request.on('end', () => {
      const body = JSON.parse(raw)
      let list
      if (request.url.includes('get_media_info')) {
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ code: 0, data: { url_info: { url: `http://127.0.0.1:${ima.address().port}/download/${body.media_id}` } } }))
        return
      }
      if (!body.folder_id) list = [{ media_id: 'stage', title: '0-项目监控', media_type: 99 }]
      else if (body.folder_id === 'stage') list = [{ media_id: 'category', title: '01-项目周报', media_type: 99 }]
      else list = [
        { media_id: 'media-1', title: '0 - T - 01 - 周报一.docx', media_type: 1 },
        { media_id: 'media-2', title: '0 - T - 01 - 周报二.docx', media_type: 1 },
      ]
      response.writeHead(200, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ code: 0, data: { knowledge_list: list, is_end: true } }))
    })
  })
  await new Promise(resolve => ima.listen(0, '127.0.0.1', resolve))
  context.after(() => ima.close())

  const uploaded = []
  const uploadedFiles = []
  const portalCalls = []
  const portal = http.createServer((request, response) => {
    let raw = ''
    request.on('data', chunk => { raw += chunk })
    request.on('end', () => {
      portalCalls.push(request.url)
      if (request.url.endsWith('/files/upload')) {
        uploadedFiles.push(raw)
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ code: 'OK', message: 'success', data: { id: `file-${uploadedFiles.length}`, fileSize: 3 } }))
        return
      }
      const body = raw ? JSON.parse(raw) : {}
      let data = {}
      if (request.url.endsWith('/auth/login')) data = { accessToken: 'token' }
      else if (request.url.endsWith('/admin/bridge/ima/runs')) data = { id: 'run-1' }
      else if (request.url.endsWith('/assets')) {
        uploaded.push(body.items)
        data = { synchronizedCount: uploaded.flat().length }
      } else if (request.url.endsWith('/complete')) data = { message: '同步完成' }
      response.writeHead(200, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ code: 'OK', message: 'success', data }))
    })
  })
  await new Promise(resolve => portal.listen(0, '127.0.0.1', resolve))
  context.after(() => portal.close())

  await synchronize({
    imaBase: `http://127.0.0.1:${ima.address().port}`,
    portalApi: `http://127.0.0.1:${portal.address().port}/api/v1`,
    clientId: 'client', apiKey: 'key', knowledgeBaseId: 'knowledge', shareId: 'share',
    username: 'test-user', password: 'test-only', rootFolderId: null, requiredStages: [0], dryRun: false,
    retries: 0, timeout: 2000, batchSize: 1, imaRequestInterval: 0, imaStagePause: 0,
    imaRateLimitRetries: 0, imaRateLimitBackoff: 1,
  })

  assert.deepEqual(uploaded.map(batch => batch.length), [1, 1])
  assert.deepEqual(uploaded.flat().map(item => item.mediaId), ['media-1', 'media-2'])
  assert.deepEqual(uploaded.flat().map(item => item.fileId), ['file-1', 'file-2'])
  assert.equal(uploadedFiles.length, 2)
  assert.ok(uploadedFiles.every(body => body.includes('abc')))
  assert.equal(portalCalls.filter(url => url.endsWith('/assets')).length, 2)
  assert.ok(portalCalls.at(-1).endsWith('/complete'))
})
