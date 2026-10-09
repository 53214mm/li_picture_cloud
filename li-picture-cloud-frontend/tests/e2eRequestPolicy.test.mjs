import assert from 'node:assert/strict'
import test from 'node:test'
import { isBusinessWrite } from '../e2e/helpers/request-policy.js'

test('browser mutation checks allow only the exact known POST queries', () => {
  for (const path of ['/api/space/list/page/vo', '/api/picture/list/page/vo/cache']) {
    assert.equal(isBusinessWrite({ method: 'POST', path }), false)
    assert.equal(isBusinessWrite({ method: 'DELETE', path }), true)
  }
  for (const path of ['/api/companion/chat/stream', '/api/companion/feed', '/api/companion/awaken', '/api/space/add', '/api/unknown/list']) {
    assert.equal(isBusinessWrite({ method: 'POST', path }), true)
    assert.equal(isBusinessWrite({ method: 'GET', path }), false)
  }
})
