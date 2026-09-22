import test from 'node:test'
import assert from 'node:assert/strict'
import { getLandingPresentation } from '../src/utils/landingPresentation.js'

test('landing keeps authentication bootstrap neutral', () => {
  assert.deepEqual(getLandingPresentation({
    authReady: false,
    isLoggedIn: false,
    companionEnabled: true
  }), {
    primary: { label: '浏览图库', to: '/gallery' },
    secondary: null,
    companion: null,
    companionAvailability: '正在确认登录状态。图片和空间功能仍可浏览。'
  })
})

test('guest landing leads to registration and login without a disabled companion link', () => {
  assert.deepEqual(getLandingPresentation({
    authReady: true,
    isLoggedIn: false,
    companionEnabled: false
  }), {
    primary: { label: '注册', to: '/register' },
    secondary: { label: '登录', to: '/login' },
    companion: null,
    companionAvailability: '伙伴功能暂未开放。你可以先使用图片和空间功能。'
  })
})

test('member landing leads to the existing space and gallery', () => {
  assert.deepEqual(getLandingPresentation({
    authReady: true,
    isLoggedIn: true,
    companionEnabled: false
  }), {
    primary: { label: '进入空间', to: '/space/my' },
    secondary: { label: '图库', to: '/gallery' },
    companion: null,
    companionAvailability: '伙伴功能暂未开放。你可以先使用图片和空间功能。'
  })
})

test('enabled companion entry follows authentication state', () => {
  assert.deepEqual(
    getLandingPresentation({ authReady: true, isLoggedIn: false, companionEnabled: true }).companion,
    { label: '登录后查看伙伴', to: { path: '/login', query: { redirect: '/companion' } } }
  )
  assert.deepEqual(
    getLandingPresentation({ authReady: true, isLoggedIn: true, companionEnabled: true }).companion,
    { label: '进入伙伴', to: '/companion' }
  )
})
