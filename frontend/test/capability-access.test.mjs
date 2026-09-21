import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { createCapabilityReader, resolveCapabilitySource, capabilityErrorMessage } from '../src/capabilityAccess.ts'
import { validateCapabilityManifest } from '../src/capabilitySchema.ts'
import { toolUnavailableReason, toolExperienceAccess } from '../src/tools/runtime/availability.ts'
import { buildDemoHistory } from '../src/data/historyDemo.ts'
import { createDemoCapabilityState, demoCapabilityAssets, saveDemoCapabilityDraft, transitionDemoCapability } from '../src/demoCapabilityGovernance.ts'
import { parseXfyunTranscript } from '../scripts/meeting-minutes-demo.mjs'

const directory = new URL('../../capabilities/', import.meta.url)
const bundled = readdirSync(directory, { withFileTypes: true }).filter(item => item.isDirectory()).map(item => new URL(`./${item.name}/capability.json`, directory)).filter(url => {
  try { readFileSync(url); return true } catch { return false }
}).map(url => JSON.parse(readFileSync(url, 'utf8')))
// Published record fixture for backend contract tests; source packages remain drafts.
const converter = structuredClone(bundled.find(item => item.identity.slug === 'document-converter'))
converter.governance = { status: 'PUBLISHED', reviewer: '测试复核人', reviewedAt: '2026-09-05', changeNote: '仅测试夹具' }
converter.delivery.online.enabled = true
const meeting = bundled.find(item => item.identity.slug === 'meeting-minutes')
const clone = value => structuredClone(value)
function record(manifest) {
  return { id: 'test-record', slug: manifest.identity.slug, name: manifest.identity.name, kind: manifest.kind, version: manifest.identity.version, status: manifest.governance.status, manifest }
}
function apiReturning(value) { return { capability: async () => value, capabilities: async () => [value] } }

test('all source manifests pass the same intake validation and remain drafts', () => {
  assert.equal(bundled.length, 4)
  assert.ok(bundled.every(item => item.governance.status === 'DRAFT'))
  for (const manifest of bundled) {
    assert.deepEqual(validateCapabilityManifest(manifest), [], manifest.identity.slug)
    assert.match(manifest.identity.version, /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/, manifest.identity.slug)
  }
})

test('meeting-minutes demo adapter converts provider output into editable transcript text', () => {
  const transcript = parseXfyunTranscript(JSON.stringify({ lattice: [
    { onebest: '项目进度正常。' },
    { json_1best: JSON.stringify({ st: { rt: [{ ws: [{ cw: [{ w: '下周' }] }, { cw: [{ w: '完成联调。' }] }] }] } }) },
  ] }))
  assert.equal(transcript, '项目进度正常。\n下周完成联调。')
})

test('approval demo keeps a published version while another version moves through review', () => {
  const expected = {
    IN_REVIEW: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT'],
    APPROVED: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT', 'CAPABILITY_APPROVE'],
    PUBLISHED: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT', 'CAPABILITY_APPROVE', 'CAPABILITY_PUBLISH'],
    DELISTED: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT', 'CAPABILITY_APPROVE', 'CAPABILITY_PUBLISH', 'CAPABILITY_UNPUBLISH'],
    ARCHIVED: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT', 'CAPABILITY_APPROVE', 'CAPABILITY_ARCHIVE'],
    REJECTED: ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT', 'CAPABILITY_REJECT'],
  }
  for (const [status, actions] of Object.entries(expected)) {
    const publishedVersion = '1.0.0'
    const version = ['PUBLISHED', 'DELISTED'].includes(status) ? publishedVersion : '1.0.1'
    const record = buildDemoHistory({ slug: `fixture-${status.toLowerCase()}`, kind: 'skill', name: status, version, publishedVersion, status, submitter: 'user-1001' })
    assert.deepEqual(record.versions[0].steps.map(step => step.action), actions, status)
    assert.equal(record.versions[0].current, status === 'PUBLISHED', status)
    assert.equal(record.versions.length, 2, status)
    const currentPublished = record.versions.filter(item => item.current)
    assert.equal(currentPublished.length, status === 'DELISTED' ? 0 : 1, status)
    if (!['PUBLISHED', 'DELISTED'].includes(status)) {
      assert.equal(record.versions[1].version, publishedVersion, status)
      assert.equal(record.versions[1].status, 'PUBLISHED', status)
      assert.equal(record.versions[1].assetStatus, 'PUBLISHED', status)
      assert.equal(record.versions[1].current, true, status)
    } else {
      assert.equal(record.versions[1].status, 'ARCHIVED', status)
      assert.equal(record.versions[1].current, false, status)
      assert.equal(record.versions[1].steps.at(-1).action, 'CAPABILITY_ARCHIVE', status)
    }
  }
})

test('demo governance keeps all assets published while later versions enter review', () => {
  const state = createDemoCapabilityState(bundled)
  const assets = demoCapabilityAssets(state)
  assert.equal(assets.length, 4)
  assert.equal(assets.filter(item => item.kind === 'skill' && item.currentlyPublished).length, 1)
  assert.equal(assets.filter(item => item.kind === 'tool' && item.currentlyPublished).length, 3)
  assert.equal(state.records.filter(item => item.kind === 'skill' && item.demoLifecycle === 'IN_REVIEW').length, 0)
  assert.equal(state.records.filter(item => item.kind === 'skill' && item.demoLifecycle === 'APPROVED').length, 0)

  const source = structuredClone(bundled.find(item => item.identity.slug === 'document-converter'))
  source.identity.version = '9.9.9'
  source.governance.changeNote = '验证提交与发布闭环'
  const draft = saveDemoCapabilityDraft(state, source, '当前员工')
  transitionDemoCapability(state, draft.versionId, 'submit', '当前员工', source.governance.changeNote)
  const submitted = state.records.find(item => item.versionId === draft.versionId)
  assert.equal(submitted.status, 'IN_REVIEW')
  assert.equal(state.records.filter(item => item.slug === source.identity.slug && item.currentlyPublished).length, 1)

  transitionDemoCapability(state, draft.versionId, 'approve', '独立审核人', '审核通过')
  transitionDemoCapability(state, draft.versionId, 'publish', '独立审核人', '发布')
  assert.equal(state.records.find(item => item.versionId === draft.versionId).currentlyPublished, true)
  assert.equal(state.records.filter(item => item.slug === source.identity.slug && item.currentlyPublished).length, 1)
  transitionDemoCapability(state, draft.versionId, 'unpublish', '平台管理员', '维护下架')
  assert.equal(state.records.filter(item => item.slug === source.identity.slug && item.currentlyPublished).length, 0)
  assert.equal(demoCapabilityAssets(state).find(item => item.slug === source.identity.slug).demoLifecycle, 'DELISTED')
})

test('local preview is explicit and never calls the backend', async () => {
  const unavailableApi = { capabilities: () => assert.fail('must not call backend'), capability: () => assert.fail('must not call backend') }
  const reader = createCapabilityReader('bundled', bundled, unavailableApi)
  assert.equal((await reader.list('skill')).length, 0)
  assert.equal((await reader.get('document-converter')).source, 'bundled')
  await assert.rejects(reader.get('unknown-capability'), /本地资料/)
  assert.equal(resolveCapabilitySource(undefined, false), 'backend')
  assert.equal(resolveCapabilitySource(undefined, true), 'bundled')
  assert.equal(resolveCapabilitySource('backend', true), 'backend')
  assert.throws(() => resolveCapabilitySource('other', false))
})

for (const status of [401, 403, 404, 500, 503]) {
  test(`HTTP ${status} never restores a bundled capability`, async () => {
    const failure = { response: { status } }
    const api = { capability: async () => { throw failure }, capabilities: async () => { throw failure } }
    const reader = createCapabilityReader('backend', bundled, api)
    await assert.rejects(reader.get('document-converter'), cause => cause === failure)
    await assert.rejects(reader.list('tool'), cause => cause === failure)
    assert.ok(capabilityErrorMessage(failure).length)
  })
}

test('an empty published catalog remains empty', async () => {
  const reader = createCapabilityReader('backend', bundled, { ...apiReturning(null), capabilities: async () => [] })
  assert.deepEqual(await reader.list('tool'), [])
})

test('backend-only tools can reuse the existing renderer and executor', async () => {
  const next = clone(converter)
  next.identity.slug = 'team-markdown-export'
  next.identity.name = '团队文档导出'
  next.identity.version = '2.0.0'
  next.runtime.route = next.delivery.online.route = '/tools/team-markdown-export'
  next.runtime.operations = [next.runtime.operations[0]]
  const reader = createCapabilityReader('backend', bundled, apiReturning(record(next)))
  const selected = await reader.get(next.identity.slug)
  assert.equal(selected.manifest.identity.version, '2.0.0')
  assert.equal(selected.manifest.runtime.operations.length, 1)
  assert.equal(toolUnavailableReason(selected), '')
})

test('unpublished or inconsistent records are rejected', async () => {
  for (const change of [
    value => { value.status = 'ARCHIVED' },
    value => { value.manifest.governance.status = 'DRAFT' },
    value => { value.version = '999.0.0' },
    value => { value.slug = 'another-tool' },
  ]) {
    const value = record(clone(converter)); change(value)
    const reader = createCapabilityReader('backend', bundled, apiReturning(value))
    await assert.rejects(reader.get('document-converter'))
  }
})

test('meeting jobs require a published, enabled backend capability', () => {
  assert.ok(toolUnavailableReason({ manifest: meeting, source: 'bundled' }))
  const published = clone(meeting)
  published.governance.status = 'PUBLISHED'
  published.delivery.online.enabled = true
  assert.ok(toolUnavailableReason({ manifest: published, source: 'bundled' }))
  assert.equal(toolUnavailableReason({ manifest: published, source: 'backend' }), '')
  published.delivery.online.enabled = false
  assert.ok(toolUnavailableReason({ manifest: published, source: 'backend' }))
})

test('local draft converter retains a working experience without claiming publication', () => {
  const draft = bundled.find(item => item.identity.slug === 'document-converter')
  assert.equal(draft.governance.status, 'DRAFT')
  assert.equal(draft.delivery.online.enabled, false)
  assert.deepEqual(toolExperienceAccess({ manifest: draft, source: 'bundled' }), { reason: '', serverPreview: false })
  assert.ok(toolExperienceAccess({ manifest: draft, source: 'backend' }).reason)
})

test('meeting workspace remains accessible while server processing stays disabled', () => {
  assert.deepEqual(toolExperienceAccess({ manifest: meeting, source: 'bundled' }), { reason: '', serverPreview: true })
  const published = clone(meeting); published.governance.status = 'PUBLISHED'
  assert.deepEqual(toolExperienceAccess({ manifest: published, source: 'backend' }), { reason: '', serverPreview: true })
  assert.ok(toolUnavailableReason({ manifest: published, source: 'backend' }))
  published.delivery.online.enabled = true
  assert.deepEqual(toolExperienceAccess({ manifest: published, source: 'backend' }), { reason: '', serverPreview: false })
  assert.equal(toolExperienceAccess({ manifest: published, source: 'bundled' }).serverPreview, true)
})

test('experience cannot revive archived tools or bypass invalid runtime definitions', () => {
  for (const source of ['bundled', 'backend']) {
    const archived = clone(converter); archived.governance.status = 'ARCHIVED'
    assert.ok(toolExperienceAccess({ manifest: archived, source }).reason)
    const invalid = clone(converter); invalid.runtime.operations[0].executor = 'unknown'
    assert.ok(toolExperienceAccess({ manifest: invalid, source }).reason)
    const wrongRoute = clone(meeting); wrongRoute.runtime.route = '/tools/other'
    assert.ok(toolExperienceAccess({ manifest: wrongRoute, source }).reason)
  }
  const disabled = clone(converter); disabled.delivery.online.enabled = false
  assert.ok(toolExperienceAccess({ manifest: disabled, source: 'backend' }).reason)
})

test('unregistered handlers and executors fail intake', () => {
  const invalidMeeting = clone(meeting); invalidMeeting.runtime.handler = 'unknown-handler'
  assert.ok(validateCapabilityManifest(invalidMeeting).some(error => error.includes('handler')))
  const invalidConverter = clone(converter); invalidConverter.runtime.operations[0].executor = 'execute-arbitrary-code'
  assert.ok(validateCapabilityManifest(invalidConverter).some(error => error.includes('执行器')))
})

test('malformed usage and operation arrays produce validation errors', () => {
  for (const bad of [null, 'operation', {}, [null]]) {
    const invalid = clone(converter); invalid.runtime.operations = bad
    assert.ok(validateCapabilityManifest(invalid).length)
  }
  const invalid = clone(converter); invalid.usage.inputs = 'not-an-array'
  assert.ok(validateCapabilityManifest(invalid).length)
})

test('malformed display fields cannot reach the shared page renderer', () => {
  for (const change of [
    value => { value.identity.name = {} },
    value => { value.classification.tags = [null] },
    value => { value.usage.inputs[0].formats = 'docx' },
    value => { value.references.manual = {} },
    value => { value.runtime.templates = 'meeting' },
    value => { value.runtime.operations[0].output.kind = 'script' },
  ]) {
    const invalid = clone(converter); change(invalid)
    assert.ok(validateCapabilityManifest(invalid).length)
  }
})

test('adapter is excluded from both catalogs but remains available to the guarded admin detail', async () => {
  const manifest = clone(bundled.find(item => item.identity.slug === 'platform-skill-adapter'))
  manifest.governance = { status: 'PUBLISHED', reviewer: '测试审核人', reviewedAt: '2026-09-08', changeNote: '测试夹具' }
  for (const source of ['bundled', 'backend']) {
    const reader = createCapabilityReader(source, bundled, apiReturning(record(manifest)))
    assert.ok((await reader.list('skill')).every(item => item.manifest.identity.slug !== 'platform-skill-adapter'))
    assert.equal((await reader.get('platform-skill-adapter')).manifest.identity.slug, 'platform-skill-adapter')
  }
})
