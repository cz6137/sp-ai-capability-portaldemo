import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { Readable } from 'node:stream'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))

export function loadDotEnv(file) {
  if (!fs.existsSync(file)) return
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const value = line.trim()
    if (!value || value.startsWith('#')) continue
    const separator = value.indexOf('=')
    if (separator < 1) continue
    const key = value.slice(0, separator).trim()
    let raw = value.slice(separator + 1).trim()
    if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) raw = raw.slice(1, -1)
    if (process.env[key] === undefined) process.env[key] = raw
  }
}

export function stageNumber(title = '') {
  const leading = String(title).trim().match(/^([0-8])(?:\s*[-_.、]|\s)/)
  if (leading) return Number(leading[1])
  const aliases = [
    ['项目监控', '项目管理'], ['预投立项', '项目启动'], ['需求分析'], ['系统设计'], ['编码实现'],
    ['数据处理'], ['部署测试'], ['运行维护'], ['总结验收'],
  ]
  return aliases.findIndex(names => names.some(name => String(title).includes(name)))
}

export function isFolder(item) {
  return Number(item?.media_type) === 99 || String(item?.media_type || '').toUpperCase() === 'FOLDER'
}

export function numberPrefix(title = '') {
  return String(title).trim().match(/^(\d+)/)?.[1] || '99'
}

export function stripNumber(title = '') {
  return String(title).replace(/^\d+\s*[-_.、]?\s*/, '').trim() || '未分类'
}

export function displayName(title = '') {
  const parts = String(title).split(' - ')
  return parts.length >= 4 ? parts.slice(3).join(' - ').trim() : String(title).trim()
}

export function assetType(title = '') {
  const value = String(title)
  if (value.includes(' - C - ')) return 'C'
  if (value.includes(' - A - ') || value.toLowerCase().includes('skill')) return 'A'
  return 'T'
}

export function normalizePortalApi(value) {
  const base = String(value || '').replace(/\/+$/, '')
  return base.endsWith('/api/v1') ? base : `${base}/api/v1`
}

function required(name) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`缺少配置 ${name}`)
  return value
}

function integer(name, fallback, min, max) {
  const value = Number(process.env[name] || fallback)
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${name} 必须在 ${min}-${max} 之间`)
  return value
}

function stageList(name, fallback) {
  const raw = process.env[name] === undefined ? fallback : process.env[name]
  if (!String(raw).trim()) return []
  const values = [...new Set(String(raw).split(',').map(value => Number(value.trim())))]
  if (values.some(value => !Number.isInteger(value) || value < 0 || value > 8)) throw new Error(`${name} 只能包含 0-8，以逗号分隔`)
  return values
}

function config(dryRun) {
  const result = {
    imaBase: required('IMA_BASE_URL').replace(/\/+$/, ''),
    clientId: required('IMA_CLIENT_ID'),
    apiKey: required('IMA_API_KEY'),
    knowledgeBaseId: required('IMA_KNOWLEDGE_BASE_ID'),
    shareId: required('IMA_SHARE_ID'),
    rootFolderId: process.env.IMA_ROOT_FOLDER_ID?.trim() || null,
    batchSize: integer('BRIDGE_BATCH_SIZE', 50, 1, 200),
    timeout: integer('BRIDGE_TIMEOUT_MS', 45000, 1000, 300000),
    retries: integer('BRIDGE_RETRIES', 3, 0, 10),
    imaPageSize: integer('IMA_PAGE_SIZE', 50, 1, 50),
    imaRequestInterval: integer('IMA_REQUEST_INTERVAL_MS', 1500, 0, 60000),
    imaStagePause: integer('IMA_STAGE_PAUSE_MS', 2000, 0, 300000),
    imaRateLimitRetries: integer('IMA_RATE_LIMIT_RETRIES', 10, 0, 30),
    imaRateLimitBackoff: integer('IMA_RATE_LIMIT_BACKOFF_MS', 10000, 100, 300000),
    imaFileRetries: integer('IMA_FILE_RETRIES', 3, 0, 10),
    imaMaxFileBytes: integer('IMA_MAX_FILE_SIZE_MB', 300, 1, 300) * 1024 * 1024,
    requiredStages: stageList('BRIDGE_REQUIRED_STAGES', '0,1,2,3,4,5,6,7,8'),
    dryRun,
  }
  if (!dryRun) {
    result.portalApi = normalizePortalApi(required('PORTAL_BASE_URL'))
    result.username = required('PORTAL_USERNAME')
    result.password = required('PORTAL_PASSWORD')
  }
  return result
}

function wait(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

async function throttleIma(cfg) {
  const interval = cfg.imaRequestInterval || 0
  const elapsed = Date.now() - (cfg.lastImaRequestAt || 0)
  if (elapsed < interval) await wait(interval - elapsed)
  cfg.lastImaRequestAt = Date.now()
}

export function isImaRateLimit(response) {
  const code = Number(response?.code)
  const message = String(response?.msg || response?.message || '')
  return code === 429 || /频率|超限|稍后重试|too many|rate.?limit/i.test(message)
}

async function requestJson(url, options, cfg) {
  let lastError
  for (let attempt = 0; attempt <= cfg.retries; attempt++) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), cfg.timeout)
    try {
      const response = await fetch(url, { ...options, signal: controller.signal })
      const text = await response.text()
      let body
      try { body = text ? JSON.parse(text) : {} } catch { throw new Error(`服务返回了非 JSON 内容（HTTP ${response.status}）`) }
      if (!response.ok) {
        const error = new Error(body.message || body.msg || `HTTP ${response.status}`)
        error.status = response.status
        error.retryable = response.status === 429 || response.status >= 500
        throw error
      }
      return body
    } catch (error) {
      lastError = error
      const retryable = error.retryable !== false && (error.name === 'AbortError' || error.retryable || error instanceof TypeError)
      if (!retryable || attempt === cfg.retries) break
      await wait(Math.min(1000 * (2 ** attempt), 8000))
    } finally {
      clearTimeout(timer)
    }
  }
  throw lastError
}

function jsonOptions(body, headers = {}) {
  return { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) }
}

async function imaPost(cfg, endpoint, body) {
  const retries = cfg.imaRateLimitRetries ?? 3
  const initialBackoff = cfg.imaRateLimitBackoff ?? 1000
  for (let attempt = 0; attempt <= retries; attempt++) {
    await throttleIma(cfg)
    const response = await requestJson(`${cfg.imaBase}/${endpoint.replace(/^\/+/, '')}`, jsonOptions(body, {
      'ima-openapi-clientid': cfg.clientId,
      'ima-openapi-apikey': cfg.apiKey,
    }), cfg)
    if (Number(response.code) === 0) return response.data || {}
    if (isImaRateLimit(response) && attempt < retries) {
      const delay = Math.min(initialBackoff * (2 ** attempt), 60000)
      console.warn(`IMA 请求受限，${Math.ceil(delay / 1000)} 秒后重试（${attempt + 1}/${retries}）...`)
      await wait(delay)
      continue
    }
    const error = new Error(`IMA 接口失败：${response.msg || response.message || response.code}`)
    error.retryable = false
    throw error
  }
  throw new Error('IMA 请求重试次数已用尽')
}

async function mediaDownloadUrl(cfg, mediaId) {
  const data = await imaPost(cfg, 'openapi/wiki/v1/get_media_info', { media_id: mediaId })
  const url = data?.url_info?.url || data?.url
  if (!url) throw new Error(`IMA 未返回文件下载地址：${mediaId}`)
  return String(url)
}

async function downloadImaFile(cfg, url) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), cfg.timeout)
  try {
    const response = await fetch(url, { signal: controller.signal })
    if (!response.ok || !response.body) {
      const error = new Error(`IMA 文件下载失败：HTTP ${response.status}`)
      error.status = response.status
      error.retryable = response.status === 429 || response.status >= 500
      throw error
    }
    const size = Number(response.headers.get('content-length') || 0)
    if (size > cfg.imaMaxFileBytes) {
      const error = new Error(`IMA 文件超过 ${Math.floor(cfg.imaMaxFileBytes / 1024 / 1024)} MB 上限：${url}`)
      error.retryable = false
      throw error
    }
    return {
      response,
      dispose: () => clearTimeout(timer),
    }
  } catch (error) {
    clearTimeout(timer)
    if (error.name === 'AbortError') error.retryable = true
    throw error
  }
}

function multipartFileBody(response, fileName, contentType, boundary) {
  const safeName = String(fileName || 'ima-file').replace(/[\r\n"]/g, '_')
  const safeType = String(contentType || 'application/octet-stream').replace(/[\r\n]/g, '')
  const opening = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${safeName}"\r\nContent-Type: ${safeType}\r\n\r\n`
  const closing = `\r\n--${boundary}--\r\n`
  return Readable.from((async function* () {
    yield Buffer.from(opening, 'utf8')
    for await (const chunk of Readable.fromWeb(response.body)) yield chunk
    yield Buffer.from(closing, 'utf8')
  })())
}

async function portalUpload(cfg, response, fileName, token) {
  const boundary = `----sp-ai-portal-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const body = multipartFileBody(response, fileName, response.headers.get('content-type'), boundary)
  const requestCfg = { ...cfg, retries: 0 }
  const result = await requestJson(`${cfg.portalApi}/files/upload`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': `multipart/form-data; boundary=${boundary}` },
    body,
    duplex: 'half',
  }, requestCfg)
  if (result.code !== 'OK' || !result.data?.id) {
    const error = new Error(`基线库文件上传失败：${result.message || result.code}`)
    error.retryable = false
    throw error
  }
  return result.data
}

async function portalPost(cfg, endpoint, body, token) {
  const headers = token ? { authorization: `Bearer ${token}` } : {}
  const response = await requestJson(`${cfg.portalApi}/${endpoint.replace(/^\/+/, '')}`, jsonOptions(body || {}, headers), cfg)
  if (response.code !== 'OK') {
    const error = new Error(`基线库接口失败：${response.message || response.code}`)
    error.retryable = false
    throw error
  }
  return response.data
}

async function* iterateFolder(cfg, folderId) {
  let cursor = ''
  for (let page = 0; page < 1000; page++) {
    const body = { knowledge_base_id: cfg.knowledgeBaseId, cursor, limit: cfg.imaPageSize || 50 }
    if (folderId) body.folder_id = folderId
    const data = await imaPost(cfg, 'openapi/wiki/v1/get_knowledge_list', body)
    for (const item of data.knowledge_list || []) yield item
    if (data.is_end === true) return
    cursor = String(data.next_cursor || '')
    if (!cursor) return
  }
  throw new Error(`IMA 目录分页超过安全上限：${folderId || 'ROOT'}`)
}

async function listFolder(cfg, folderId) {
  const items = []
  for await (const item of iterateFolder(cfg, folderId)) items.push(item)
  return items
}

function toAsset(item, context) {
  const mediaId = String(item.media_id || '').trim()
  if (!mediaId) return null
  const title = String(item.title || item.name || mediaId)
  return {
    mediaId,
    name: displayName(title),
    type: context.forceCase ? 'C' : assetType(title),
    stageId: context.stageId,
    categoryName: context.categoryName,
    categoryNumber: context.categoryNumber,
    folderId: context.categoryFolderId,
    caseCategoryName: context.caseCategoryName || null,
    caseCategoryFolderId: context.caseCategoryFolderId || null,
    metadata: item,
  }
}

async function walkFiles(cfg, folderId, context, emit) {
  for await (const child of iterateFolder(cfg, folderId)) {
    if (isFolder(child)) await walkFiles(cfg, String(child.media_id), context, emit)
    else {
      const asset = toAsset(child, context)
      if (asset) await emit(asset)
    }
  }
}

async function collectStage(cfg, stageId, stageFolderId, emit) {
  for await (const child of iterateFolder(cfg, stageFolderId)) {
    if (isFolder(child)) {
      const context = {
        stageId,
        categoryName: stripNumber(child.title),
        categoryNumber: numberPrefix(child.title),
        categoryFolderId: String(child.media_id),
      }
      await walkFiles(cfg, String(child.media_id), context, emit)
    } else {
      const asset = toAsset(child, {
        stageId, categoryName: '未分类', categoryNumber: '99', categoryFolderId: null,
      })
      if (asset) await emit(asset)
    }
  }
}

async function collectCases(cfg, root, emit) {
  for await (const business of iterateFolder(cfg, String(root.media_id))) {
    if (!isFolder(business)) continue
    const businessId = String(business.media_id)
    const context = {
      stageId: 8,
      categoryName: '标杆案例归纳',
      categoryNumber: '99',
      categoryFolderId: String(root.media_id),
      caseCategoryName: stripNumber(business.title),
      caseCategoryFolderId: businessId,
      forceCase: true,
    }
    await walkFiles(cfg, businessId, context, emit)
  }
}

async function discoverImaRoots(cfg) {
  const discoveredStages = new Set()
  const roots = await listFolder(cfg, cfg.rootFolderId)
  for (const root of roots) {
    if (!isFolder(root)) continue
    const title = String(root.title || '')
    if (!title.includes('标杆案例') && !title.includes('案例归纳')) {
      const stage = stageNumber(title)
      if (stage >= 0) discoveredStages.add(stage)
    }
  }
  return { roots, discoveredStages: [...discoveredStages].sort() }
}

async function streamImaMetadata(cfg, roots, emit, progress, afterRoot) {
  for (const root of roots) {
    if (!isFolder(root)) continue
    const title = String(root.title || '')
    const before = progress.total
    if (title.includes('标杆案例') || title.includes('案例归纳')) await collectCases(cfg, root, emit)
    else {
      const stage = stageNumber(title)
      if (stage >= 0) await collectStage(cfg, stage, String(root.media_id), emit)
      else continue
    }
    if (afterRoot) await afterRoot()
    console.log(`IMA 分批读取：${title}，本环节 ${progress.total - before} 条，累计 ${progress.total} 条`)
    if (cfg.imaStagePause) await wait(cfg.imaStagePause)
  }
}

function validateStageRoots(cfg, discoveredStages) {
  const missingStages = cfg.requiredStages.filter(stage => !discoveredStages.includes(stage))
  if (missingStages.length) throw new Error(`未发现必需的环节目录：${missingStages.join(', ')}；请检查 IMA 目录命名或 BRIDGE_REQUIRED_STAGES`)
}

export async function collectImaMetadata(cfg) {
  const discovery = await discoverImaRoots(cfg)
  const output = new Map()
  const progress = { total: 0 }
  await streamImaMetadata(cfg, discovery.roots, async asset => {
    if (output.has(asset.mediaId)) return
    output.set(asset.mediaId, asset)
    progress.total++
  }, progress)
  return { items: [...output.values()], discoveredStages: discovery.discoveredStages }
}

export async function synchronize(cfg) {
  console.log('正在分批读取 IMA 元数据...')
  const discovery = await discoverImaRoots(cfg)
  validateStageRoots(cfg, discovery.discoveredStages)

  if (cfg.dryRun) {
    const seen = new Set()
    const progress = { total: 0 }
    const byStage = {}
    await streamImaMetadata(cfg, discovery.roots, async asset => {
      if (seen.has(asset.mediaId)) return
      seen.add(asset.mediaId); progress.total++
      byStage[asset.stageId] = (byStage[asset.stageId] || 0) + 1
    }, progress)
    console.log(`读取完成：${progress.total} 条；环节统计：${JSON.stringify(byStage)}`)
    if (progress.total === 0) throw new Error('IMA 未读取到任何资产，已阻止空快照同步')
    return
  }

  let token = ''
  let tokenIssuedAt = 0
  const loginPortal = async () => {
    const login = await portalPost(cfg, 'auth/login', { username: cfg.username, password: cfg.password })
    token = login.accessToken
    tokenIssuedAt = Date.now()
    if (!token) throw new Error('基线库登录响应中没有 accessToken')
  }
  const portalCall = async (endpoint, body) => {
    if (!token || Date.now() - tokenIssuedAt >= 12 * 60 * 1000) await loginPortal()
    try { return await portalPost(cfg, endpoint, body, token) }
    catch (error) {
      if (error.status !== 401) throw error
      await loginPortal()
      return portalPost(cfg, endpoint, body, token)
    }
  }
  const storeImaFile = async asset => {
    let lastError
    const retries = cfg.imaFileRetries ?? 3
    for (let attempt = 0; attempt <= retries; attempt++) {
      let downloaded
      try {
        const url = await mediaDownloadUrl(cfg, asset.mediaId)
        downloaded = await downloadImaFile(cfg, url)
        if (!token || Date.now() - tokenIssuedAt >= 12 * 60 * 1000) await loginPortal()
        const file = await portalUpload(cfg, downloaded.response, asset.name, token)
        console.log(`MongoDB 文件入库：${asset.name} (${Math.ceil(file.fileSize / 1024)} KB)`)
        return file.id
      } catch (error) {
        lastError = error
        const retryable = error.status === 401 || error.retryable === true || error.name === 'AbortError' || error instanceof TypeError
        if (!retryable || attempt === retries) throw error
        if (error.status === 401) await loginPortal()
        const delay = Math.min(2000 * (2 ** attempt), 30000)
        console.warn(`文件同步重试：${asset.name}，${Math.ceil(delay / 1000)} 秒后继续（${attempt + 1}/${retries}）...`)
        await wait(delay)
      } finally {
        if (downloaded) downloaded.dispose()
      }
    }
    throw lastError || new Error(`文件同步失败：${asset.name}`)
  }
  await loginPortal()
  const run = await portalCall('admin/bridge/ima/runs', {
    knowledgeBaseId: cfg.knowledgeBaseId,
    shareId: cfg.shareId,
  })
  let completed = false
  try {
    const seen = new Set()
    const progress = { total: 0 }
    let batch = []
    const flush = async () => {
      if (!batch.length) return
      const uploading = batch
      batch = []
      const result = await portalCall(`admin/bridge/ima/runs/${run.id}/assets`, { items: uploading })
      console.log(`基线库分批写入：本批 ${uploading.length} 条，服务端累计 ${result.synchronizedCount} 条`)
    }
    await streamImaMetadata(cfg, discovery.roots, async asset => {
      if (seen.has(asset.mediaId)) return
      asset.fileId = await storeImaFile(asset)
      seen.add(asset.mediaId); progress.total++
      batch.push(asset)
      if (batch.length >= cfg.batchSize) await flush()
    }, progress, flush)
    await flush()
    if (progress.total === 0) throw new Error('IMA 未读取到任何资产，已阻止空快照同步')
    const result = await portalCall(`admin/bridge/ima/runs/${run.id}/complete`, {})
    completed = true
    console.log(result.message)
  } catch (error) {
    try { await portalCall(`admin/bridge/ima/runs/${run.id}/fail`, { message: String(error.message || error).slice(0, 1800) }) }
    catch (failError) { console.error(`批次失败状态回报未成功：${failError.message}`) }
    throw error
  } finally {
    if (!completed) console.error(`同步未完成，批次 ${run.id} 不会停用基线库中的既有数据。`)
  }
}

async function main() {
  loadDotEnv(path.resolve(SCRIPT_DIR, '..', '.env'))
  loadDotEnv(path.resolve(process.cwd(), '.env'))
  const dryRun = process.argv.includes('--dry-run')
  const cfg = config(dryRun)
  await synchronize(cfg)
}

const entry = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : ''
if (import.meta.url === entry) {
  main().catch(error => {
    console.error(`IMA 摆渡失败：${error.message || error}`)
    process.exitCode = 1
  })
}
