import test from 'node:test'
import assert from 'node:assert/strict'
import { isObjectiveGrowthReason } from '../src/presentation/companionGrowthReason.js'
test('explicit visual-observation envelope is attributed to image observation', () => {
  assert.equal(isObjectiveGrowthReason('可见观察：画面里有一只猫。\n不确定解读：可能在休息。'), true)
  assert.equal(isObjectiveGrowthReason('  可见观察：一张桌子。'), true)
})
test('legacy narrative and absent reasons retain existing presentation', () => {
  for (const reason of [null, undefined, '', 42, '我喜欢这个颜色。', '旧记录提到了可见观察：但不是新格式']) assert.equal(isObjectiveGrowthReason(reason), false)
})
