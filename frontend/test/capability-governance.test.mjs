import test from 'node:test'
import assert from 'node:assert/strict'
import { draftCapability, availableCapabilityActions, capabilityStatusLabel, isCapabilityContributor } from '../src/capabilityGovernance.ts'

function record(status, extra = {}) { return { versionId: 'v1', currentVersionId: 'v1', updatedAt: '2026-09-05T10:00:00Z', status, assetStatus: status, createdBy: 'creator', currentlyPublished: false, reviewApproved: false, ...extra } }
test('new versions clear the old approval and require their own version and change note', () => {
  const source = { identity: { version: '1.0' }, governance: { status: 'PUBLISHED', reviewer: 'old-reviewer', reviewedAt: '2026-09-01', changeNote: 'old' } }
  const draft = draftCapability(source, true)
  assert.equal(draft.identity.version, '')
  assert.deepEqual(draft.governance, { status: 'DRAFT', changeNote: '' })
  assert.equal(source.governance.status, 'PUBLISHED')
})
test('contributors cannot review but can publish a version approved by another account', () => {
  assert.deepEqual(availableCapabilityActions(record('IN_REVIEW'), true), [])
  assert.deepEqual(availableCapabilityActions(record('IN_REVIEW'), false), ['approve', 'reject'])
  assert.deepEqual(availableCapabilityActions(record('IN_REVIEW', { reviewApproved: true }), true), ['publish'])
  assert.equal(isCapabilityContributor(record('IN_REVIEW'), [], 'creator'), true)
  for (const action of ['CAPABILITY_IMPORT', 'CAPABILITY_SUBMIT']) assert.equal(isCapabilityContributor(record('IN_REVIEW'), [{ action, actorId: 'editor' }], 'editor'), true)
  assert.equal(isCapabilityContributor(record('IN_REVIEW'), [{ action: 'CAPABILITY_APPROVE', actorId: 'reviewer' }], 'reviewer'), false)
})
test('current, withdrawn, historical and archived versions have distinct actions and labels', () => {
  const current = record('PUBLISHED', { currentlyPublished: true })
  assert.deepEqual(availableCapabilityActions(current, false), ['unpublish'])
  assert.equal(capabilityStatusLabel(current), '当前发布')
  const withdrawn = record('PUBLISHED', { assetStatus: 'ARCHIVED' })
  assert.equal(capabilityStatusLabel(withdrawn), '已下架')
  assert.deepEqual(availableCapabilityActions(withdrawn, false), ['archive'])
  assert.equal(capabilityStatusLabel(record('PUBLISHED', { currentVersionId: 'v2' })), '历史发布')
  assert.deepEqual(availableCapabilityActions(record('ARCHIVED'), false), [])
})
test('legacy records without revision identifiers cannot invoke governance actions', () => {
  assert.deepEqual(availableCapabilityActions(record('DRAFT', { versionId: undefined }), false), [])
  assert.deepEqual(availableCapabilityActions(record('DRAFT', { updatedAt: undefined }), false), [])
})
