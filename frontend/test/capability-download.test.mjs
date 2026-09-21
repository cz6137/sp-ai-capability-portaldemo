import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createHash } from 'node:crypto'
import { capabilityDownloadAccess } from '../src/capabilityDownload.ts'
import { localCapabilityDownloads } from '../scripts/local-capability-downloads.mjs'

const manifest = JSON.parse(readFileSync(new URL('../../capabilities/platform-skill-adapter/capability.json', import.meta.url)))
const entry = { slug: manifest.identity.slug, version: manifest.identity.version, file: `platform-skill-adapter-${manifest.identity.version}.zip` }
test('local real packages remain downloadable drafts, but missing, stale and archived packages do not', () => {
  const selected = { source: 'bundled', manifest: structuredClone(manifest) }
  assert.equal(capabilityDownloadAccess(selected, [entry], true).reason, '')
  assert.equal(capabilityDownloadAccess(selected, [entry], true).label, '下载 Skill 包')
  assert.doesNotMatch(capabilityDownloadAccess(selected, [entry], true).label, /Demo/i)
  assert.ok(capabilityDownloadAccess(selected, [], true).reason)
  assert.ok(capabilityDownloadAccess(selected, [{ ...entry, version: 'wrong' }], true).reason)
  assert.ok(capabilityDownloadAccess(selected, [entry], false).reason)
  selected.manifest.governance.status = 'ARCHIVED'
  assert.ok(capabilityDownloadAccess(selected, [entry], true).reason)
})
test('backend downloads need a matching published file and never use local packages', () => {
  const published = structuredClone(manifest); published.governance.status = 'PUBLISHED'
  const selected = { source: 'backend', manifest: published, record: { slug: entry.slug, version: entry.version, status: 'PUBLISHED', packageFileId: 'test-file' } }
  assert.equal(capabilityDownloadAccess(selected, [entry], true).reason, '')
  assert.equal(capabilityDownloadAccess(selected, [entry], true).local, undefined)
  for (const change of [{ packageFileId: undefined }, { status: 'DRAFT' }, { version: 'wrong' }]) {
    assert.ok(capabilityDownloadAccess({ ...selected, record: { ...selected.record, ...change } }, [entry], true).reason)
  }
})
test('online-only tools explain why there is no package', () => {
  const online = structuredClone(manifest); online.kind = 'tool'; delete online.delivery.package
  assert.match(capabilityDownloadAccess({ manifest: online, source: 'bundled' }, [entry], true).reason, /在线体验/)
})

test('local download endpoint rejects unknown paths, stale manifests and corrupted files; backend mode registers no endpoint', () => {
  const root = mkdtempSync(join(tmpdir(), 'capability-download-test-'))
  const frontend = join(root, 'frontend')
  const sources = join(root, 'capabilities/platform-skill-adapter'); mkdirSync(sources, { recursive: true })
  const output = join(root, 'outputs/local-capability-downloads'); mkdirSync(output, { recursive: true })
  const hash = value => createHash('sha256').update(value).digest('hex')
  const source = JSON.stringify(manifest), data = Buffer.from('fixture package')
  writeFileSync(join(sources, 'capability.json'), source)
  writeFileSync(join(output, entry.file), data)
  writeFileSync(join(output, '交付索引.json'), JSON.stringify([{ ...entry, bytes: data.length, sha256: hash(data), manifestSha256: hash(source) }]))
  let handler
  const plugin = localCapabilityDownloads()
  plugin.configResolved({ root: frontend, base: '/sp-ai-portal-web/', env: {} })
  plugin.configureServer({ middlewares: { use(value) { handler = value } } })
  function request(path, method = 'GET') {
    const result = { statusCode: 200, headers: {}, setHeader(key, value) { this.headers[key] = value }, end(body) { this.body = body } }
    handler({ url: `/sp-ai-portal-web/__local-capability-packages/${path}`, method }, result, () => assert.fail('endpoint must handle request'))
    return result
  }
  assert.equal(JSON.parse(request('index.json').body).length, 1)
  const contentPath = `content/${entry.slug}`
  assert.deepEqual(request(contentPath).body, data)
  assert.equal(request(contentPath).headers['Content-Disposition'], undefined)
  assert.equal(request(contentPath).headers['Content-Type'], 'application/octet-stream')
  assert.equal(request(entry.file).statusCode, 404)
  assert.equal(request('unlisted.zip').statusCode, 404)
  assert.equal(request('%2e%2e%2fsecret.zip').statusCode, 404)
  assert.equal(request(contentPath, 'POST').statusCode, 405)
  writeFileSync(join(output, entry.file), Buffer.alloc(data.length, 1))
  assert.equal(request(contentPath).statusCode, 503)
  writeFileSync(join(sources, 'capability.json'), source + ' ')
  assert.equal(JSON.parse(request('index.json').body).length, 0)
  assert.equal(request(contentPath).statusCode, 404)
  const backend = localCapabilityDownloads()
  backend.configResolved({ root: frontend, base: '/', env: { VITE_CAPABILITY_SOURCE: 'backend' } })
  backend.configureServer({ middlewares: { use() { assert.fail('backend must expose no local files') } } })
  assert.equal(plugin.apply, undefined)
  assert.equal(typeof plugin.generateBundle, 'function')
  assert.equal(plugin.configurePreviewServer, undefined)
})
