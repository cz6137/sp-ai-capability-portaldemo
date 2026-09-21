import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createBaselineReader, resolveBaselineSource, summarizeBaseline } from '../src/baselineAccess.ts'

const snapshot = JSON.parse(readFileSync(new URL('../src/data/baseline-snapshot-0815.json', import.meta.url)))

test('public snapshot is intentionally empty', async () => {
  assert.deepEqual(snapshot.files, [])
  assert.equal(summarizeBaseline(snapshot.files).length, 9)
  const reader = createBaselineReader('snapshot', snapshot, { stages() { assert.fail('no remote call') }, assets() { assert.fail('no remote call') } })
  assert.deepEqual((await reader.read()).assets, [])
})

test('baseline source is explicit and backend failures do not restore private data', async () => {
  assert.equal(resolveBaselineSource(undefined, true), 'snapshot')
  assert.equal(resolveBaselineSource(undefined, false), 'backend')
  assert.throws(() => resolveBaselineSource('automatic-fallback', true))
  const api = { stages: async () => [], assets: async () => [] }
  assert.deepEqual(await createBaselineReader('backend', snapshot, api).read(), { stages: [], assets: [] })
  const error = { response: { status: 503 } }
  await assert.rejects(createBaselineReader('backend', snapshot, { ...api, assets: async () => { throw error } }).read(), value => value === error)
})

test('backend fixtures remain isolated from the public snapshot', async () => {
  const assets = [{ id: 'fixture', name: '公开测试文档', type: 'T', stage_num: 0, sub_name: '测试资料' }]
  const stages = summarizeBaseline(assets)
  const result = await createBaselineReader('backend', snapshot, { assets: async () => assets, stages: async () => stages }).read()
  assert.deepEqual(result, { stages, assets })
  assert.deepEqual(snapshot.files, [])
})
