import test from 'node:test'
import assert from 'node:assert/strict'
import { describeHabitatActivity } from '../src/presentation/companionHabitat.js'

const ready = { version: 1, availability: 'ready', freshness: 'fresh', activity: 'idle', attention: 'none' }
test('habitat labels format R06 activity without deriving mood or personal history', () => {
  for (const [activity, label] of Object.entries({ idle: '此刻在小屋里', feeding: '正在处理这次喂养', thinking: '正在等待回复', responding: '正在接收回复', acknowledging: '正在处理这条提议' })) {
    assert.equal(describeHabitatActivity({ ...ready, activity, affect: 'irritated', rapport: 'close', text: '旅行回来了，哈哈' }), label)
  }
  assert.equal(describeHabitatActivity({ ...ready, attention: 'proposal' }), '有一条待回应的提议')
  assert.equal(describeHabitatActivity({ ...ready, activity: 'thinking', attention: 'proposal', freshness: 'stale' }), '正在等待回复')
})
test('unavailable and stale habitat observations never claim a present activity', () => {
  for (const availability of ['absent', 'loading', 'error', 'unobserved', 'unavailable', 'disabled']) {
    assert.equal(describeHabitatActivity({ ...ready, availability, activity: 'feeding' }), '等待伙伴状态')
  }
  assert.equal(describeHabitatActivity(null), '等待伙伴状态')
  assert.equal(describeHabitatActivity({ ...ready, version: 2 }), '等待伙伴状态')
  assert.equal(describeHabitatActivity({ ...ready, freshness: 'stale' }), '状态待更新')
  assert.equal(describeHabitatActivity({ ...ready, activity: 'sleeping' }), '状态待更新')
})
