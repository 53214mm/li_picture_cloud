import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildSpaceCreatePayload,
  groupMySpaces,
  hasPermission,
  normalizePermissions
} from '../src/utils/spaceAccess.js'
import {
  SPACE_ROLE,
  SPACE_TYPE,
  spaceRoleText,
  spaceTypeText
} from '../src/constants/space.js'

test('groups owned private, owned team, and joined team without duplicates', () => {
  const owned = [
    { id: 1, userId: 9, spaceType: 0 },
    { id: 2, userId: 9, spaceType: 1 }
  ]
  const memberships = [
    { spaceRole: 'admin', space: { id: 2, userId: 9, spaceType: 1 } },
    { spaceRole: 'editor', space: { id: 3, userId: 8, spaceType: 1 } },
    { spaceRole: 'viewer', space: { id: 3, userId: 8, spaceType: 1 } }
  ]

  const result = groupMySpaces(owned, memberships, 9)

  assert.deepEqual(result.privateSpaces.map((item) => item.id), [1])
  assert.deepEqual(result.ownedTeamSpaces.map((item) => item.id), [2])
  assert.deepEqual(result.joinedTeamSpaces.map((item) => item.id), [3])
  assert.equal(result.joinedTeamSpaces[0].currentRole, 'editor')
})

test('permissions deny by default and accept arrays or sets', () => {
  assert.equal(hasPermission(undefined, 'picture:upload'), false)
  assert.equal(hasPermission(['picture:view'], 'picture:upload'), false)
  assert.equal(hasPermission(new Set(['picture:upload']), 'picture:upload'), true)
  assert.deepEqual(normalizePermissions(['picture:view', 'picture:view']), ['picture:view'])
})

test('space metadata matches backend values', () => {
  assert.equal(SPACE_TYPE.PRIVATE, 0)
  assert.equal(SPACE_TYPE.TEAM, 1)
  assert.equal(SPACE_ROLE.VIEWER, 'viewer')
  assert.equal(SPACE_ROLE.EDITOR, 'editor')
  assert.equal(SPACE_ROLE.ADMIN, 'admin')
  assert.equal(spaceTypeText(SPACE_TYPE.TEAM), '团队空间')
  assert.equal(spaceRoleText(SPACE_ROLE.VIEWER), '查看者')
})

test('builds an explicit space creation payload', () => {
  assert.deepEqual(buildSpaceCreatePayload({
    spaceName: ' 设计组 ',
    spaceLevel: 0,
    spaceType: 1
  }), { spaceName: '设计组', spaceLevel: 0, spaceType: 1 })

  assert.deepEqual(buildSpaceCreatePayload({
    spaceName: '   ',
    spaceLevel: 0,
    spaceType: 0
  }), { spaceLevel: 0, spaceType: 0 })
})
