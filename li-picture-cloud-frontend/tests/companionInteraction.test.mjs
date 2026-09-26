import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizePictureId, createPictureDragSession, inspectCompanionPicture, createPictureInspector, PICTURE_DRAG_TYPE } from '../src/presentation/companionInteraction.js'

const picture = { id: '11', spaceId: '51', name: '图片' }
const space = { id: '51', userId: '42', spaceType: 0 }
test('picture IDs reject coercion and precision loss', () => {
  for (const value of [null, true, [], {}, '', ' 11', '-1', '1e2', 1.5, Number.MAX_SAFE_INTEGER + 1, '9223372036854775808']) assert.equal(normalizePictureId(value), null)
  for (const value of [11, '11', '9223372036854775807']) assert.equal(normalizePictureId(value), String(value))
})
test('drag tickets are local, account-bound, single-use and invalidated on replacement/cancel', () => {
  let seed = 0
  const drag = createPictureDragSession(() => `ticket-${++seed}`)
  const a = drag.start('11', '42')
  assert.equal(drag.accepts([PICTURE_DRAG_TYPE], '42'), true)
  assert.equal(drag.accepts([PICTURE_DRAG_TYPE, 'Files'], '42'), false)
  assert.equal(drag.accepts(['text/plain'], '42'), false)
  assert.equal(drag.take('11', '42'), null)
  assert.equal(drag.take(a, '43'), null)
  assert.equal(drag.take(a, '42'), '11')
  assert.equal(drag.take(a, '42'), null)
  const b = drag.start('12', '42')
  drag.start('13', '42')
  assert.equal(drag.take(b, '42'), null)
  drag.clear()
  assert.equal(drag.accepts([PICTURE_DRAG_TYPE], '42'), false)
  assert.equal(drag.start('bad', '42'), null)
})
test('candidate re-reads server data and uses the existing private-owner UI scope', async () => {
  const calls = []
  const readPicture = async id => { calls.push(['picture', id]); return picture }
  const readSpace = async id => { calls.push(['space', id]); return space }
  assert.deepEqual(await inspectCompanionPicture('11', '42', { readPicture, readSpace }), { picture, space })
  assert.deepEqual(calls, [['picture', '11'], ['space', '51']])
  for (const invalid of [{ ...picture, id: '12' }, { ...picture, spaceId: null }]) {
    await assert.rejects(inspectCompanionPicture('11', '42', { readPicture: async () => invalid, readSpace }))
  }
  for (const invalid of [{ ...space, id: '52' }, { ...space, userId: '43' }, { ...space, spaceType: 1 }]) {
    await assert.rejects(inspectCompanionPicture('11', '42', { readPicture, readSpace: async () => invalid }))
  }
  await assert.rejects(inspectCompanionPicture('11', '42', { readPicture: async () => { throw new Error('403 private detail') }, readSpace }))
})
test('closed/replaced picture inspections cannot publish late results or private server errors', async () => {
  const pending = []
  const changes = []
  const inspector = createPictureInspector({
    readPicture: () => new Promise((resolve, reject) => pending.push({ resolve, reject })),
    readSpace: async () => space,
    onChange: value => changes.push(value)
  })
  const old = inspector.inspect('11', '42')
  inspector.clear()
  pending[0].resolve(picture)
  await old
  assert.equal(changes.at(-1), null)
  const first = inspector.inspect('11', '42')
  const second = inspector.inspect('12', '42')
  pending[2].resolve({ ...picture, id: '12' })
  await second
  assert.equal(changes.at(-1).phase, 'ready')
  pending[1].reject(new Error('secret server data'))
  await first
  assert.equal(changes.at(-1).picture.id, '12')
  const failed = inspector.inspect('13', '42')
  pending[3].reject(new Error('secret server data'))
  await failed
  assert.equal(changes.at(-1).phase, 'error')
  assert.equal(JSON.stringify(changes).includes('secret'), false)
})
