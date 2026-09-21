import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stageMaterialGroups, materialSections } from '../src/materialCatalog.ts'

const outlines = JSON.parse(readFileSync(new URL('../src/data/stage-outline.json', import.meta.url)))
const flatten = groups => groups.flatMap(group => group.sections.flatMap(section => section.items))

test('empty public materials produce no groups', () => {
  for (const outline of outlines.stages) assert.deepEqual(stageMaterialGroups([], outline.groups, `stage-${outline.stage}`), [])
})

test('new backend materials stay visible without private snapshot data', () => {
  const items = [{ name: '新交付文档', sub_name: '新的业务事项', type: 'T' }, { name: '未分类文档', type: 'C' }]
  const groups = stageMaterialGroups(items, outlines.stages[0].groups, 'new')
  assert.deepEqual(flatten(groups), items)
  assert.deepEqual(groups.map(group => group.title), ['新的业务事项', '其他材料'])
})

test('section anchors remain stable', () => {
  const items = [{ name: '甲', sub_name: '目录 A / B' }, { name: '乙', sub_name: '目录二' }]
  assert.equal(materialSections(items, 'stage-1')[1].id, materialSections(items.slice(1), 'stage-1')[0].id)
  assert.notEqual(materialSections(items, 'stage-1')[0].id, materialSections(items, 'stage-2')[0].id)
})
