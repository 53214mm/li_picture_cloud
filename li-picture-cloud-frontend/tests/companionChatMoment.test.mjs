import test from 'node:test'
import assert from 'node:assert/strict'
import { splitChatMoment } from '../src/presentation/companionChatMoment.js'

const user = content => ({ role: 'USER', content })
const reply = content => ({ role: 'COMPANION', content })

test('only the latest turn is present, earlier messages remain available unchanged', () => {
  const messages = [user('之前'), reply('旧回复'), user('现在'), reply('新\n回复')]
  const result = splitChatMoment(messages)
  assert.deepEqual(result.current, messages.slice(2))
  assert.deepEqual(result.previous, messages.slice(0, 2))
  assert.equal(messages.length, 4)
})

test('pending and failed sends never pair a new question with an old answer', () => {
  const messages = [user('之前'), reply('旧回复'), user('未完成')]
  assert.deepEqual(splitChatMoment(messages).current, [messages[2]])
  const pending = [...messages, reply('')]
  assert.deepEqual(splitChatMoment(pending).current, pending.slice(2))
  assert.deepEqual(splitChatMoment([...messages, user('再说一句')]).current, [user('再说一句')])
})

test('empty history and histories beginning with a reply have a bounded current moment', () => {
  assert.deepEqual(splitChatMoment([]), { current: [], previous: [] })
  assert.deepEqual(splitChatMoment([reply('旧'), reply('新')]), {
    current: [reply('新')], previous: [reply('旧')]
  })
})
