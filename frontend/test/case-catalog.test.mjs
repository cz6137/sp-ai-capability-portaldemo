import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCaseCatalog } from '../src/caseCatalog.ts'

test('public case catalog starts with seven empty categories', () => {
  const groups = buildCaseCatalog([], {})
  assert.deepEqual(groups.map(item => item.name), ['不动产与地籍', '空间规划与政务', '数据底座', '住建业务', '数据局业务', 'AI+', '通用/跨域'])
  assert.ok(groups.every(item => item.count === 0 && item.stageCount === 0))
})

test('untagged imported cases remain visibly unclassified', () => {
  const groups = buildCaseCatalog([{ id: 'fixture', name: '测试案例', type: 'C', stage_num: 3 }], {})
  assert.equal(groups.find(item => item.code === 'general').count, 1)
  assert.equal(groups.find(item => item.code === 'general').unclassified, 1)
})
