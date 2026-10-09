import assert from 'node:assert/strict'
import test from 'node:test'
import { formatDate } from '../src/utils/date.js'

test('picture dates render identically for ISO, epoch numbers and cached epoch strings', () => {
  const milliseconds = 1789971452000
  const expected = new Date(milliseconds).toLocaleDateString('zh-CN')
  for (const value of [milliseconds, String(milliseconds), ` ${milliseconds} `, '2026-09-21T06:17:32.000+00:00', new Date(milliseconds)]) {
    assert.equal(formatDate(value), expected)
  }
})

test('missing or invalid date values show a placeholder, never Invalid Date or a coerced date', () => {
  for (const value of [null, undefined, '', ' ', 'not-a-date', 'NaN', NaN, Infinity, {}, [], true, new Date(NaN), '8640000000000001', '9223372036854775806']) {
    assert.equal(formatDate(value), '-')
  }
})

test('zero is a valid millisecond timestamp and timezone offsets preserve the instant', () => {
  assert.equal(formatDate(0), new Date(0).toLocaleDateString('zh-CN'))
  assert.equal(formatDate('0'), formatDate(0))
  assert.equal(formatDate('2026-09-21T15:17:32+09:00'), formatDate(1789971452000))
})
