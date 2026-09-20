import test from 'node:test'
import assert from 'node:assert/strict'

const observation = await import('../src/utils/companionObservation.js')

test('formats observation labels for a readable admin list', () => {
  assert.equal(observation.displayUser({ userName: '小李', userAccount: 'li', subjectId: 7 }), '小李')
  assert.equal(observation.displayUser({ userName: '', userAccount: 'li', subjectId: 7 }), 'li')
  assert.equal(observation.displayUser({ userName: '', userAccount: '', subjectId: 7 }), '用户 #7')
  assert.equal(observation.displayPicture({ pictureName: '猫.jpg', pictureId: 102 }), '猫.jpg')
  assert.equal(observation.displayPicture({ pictureName: null, pictureId: 102 }), '图片 #102')
  assert.equal(observation.displayActualNutrition({ actualNutritionLabel: '元数据分析' }), '元数据分析')
  assert.equal(observation.displayActualNutrition({ actualNutritionLabel: null }), '尚未产出')
})

test('formats duration and maps status to stable css classes', () => {
  assert.equal(observation.formatObservationDuration(850), '850 毫秒')
  assert.equal(observation.formatObservationDuration(3200), '3.2 秒')
  assert.equal(observation.formatObservationDuration(125000), '2 分 5 秒')
  assert.equal(observation.formatObservationDuration(null), '-')
  assert.equal(observation.statusClass('COMPLETED'), 'is-success')
  assert.equal(observation.statusClass('FAILED'), 'is-failed')
  assert.equal(observation.statusClass('PROCESSING'), 'is-processing')
})

test('maps timeline states without pretending skipped stages succeeded', () => {
  assert.equal(observation.stageClass('SUCCESS'), 'is-success')
  assert.equal(observation.stageClass('FAILED'), 'is-failed')
  assert.equal(observation.stageClass('SKIPPED'), 'is-skipped')
  assert.equal(observation.stageClass('DEGRADED'), 'is-degraded')
  assert.equal(observation.stageClass('UNKNOWN'), 'is-unknown')
  assert.equal(observation.stageStatusLabel('SKIPPED'), '未执行')
  assert.equal(observation.stageStatusLabel('PROCESSING'), '处理中')
  assert.equal(observation.stageStatusLabel('UNKNOWN'), '未知')
})

test('does not describe a processing run without growth as a failed settlement', () => {
  assert.equal(observation.growthAbsenceDescription('PROCESSING'), '成长结算尚未完成。')
  assert.equal(observation.growthAbsenceDescription('FAILED'), '没有成长记录，说明结算没有生效。')
  assert.equal(observation.growthAbsenceDescription('REJECTED'), '授权未通过，本次没有进入成长结算。')
})
