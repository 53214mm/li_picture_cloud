import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildSpaceCreatePayload,
  buildMySpaceQuery,
  collaborationMode,
  copyTargets,
  groupMySpaces,
  hasPermission,
  normalizePermissions
} from '../src/utils/spaceAccess.js'

test('scopes gallery analysis space lookup to the current user', () => {
  assert.deepEqual(buildMySpaceQuery(42), {
    current: 1,
    pageSize: 20,
    userId: 42
  })
})
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

test('chooses edit, view, or no collaboration mode', () => {
  assert.equal(collaborationMode(['collaboration:join', 'collaboration:edit'], true), 'edit')
  assert.equal(collaborationMode(['collaboration:join'], true), 'view')
  assert.equal(collaborationMode(['collaboration:join'], false), null)
  assert.equal(collaborationMode([], true), null)
})

test('lists spaces a picture can be added to, skipping viewer-only teams and its current space', () => {
  const groups = {
    privateSpaces: [{ id: 1, spaceName: '我的空间' }],
    ownedTeamSpaces: [{ id: 2, spaceName: '我建的团队' }],
    joinedTeamSpaces: [
      { id: 3, spaceName: '能编辑的团队', currentRole: SPACE_ROLE.EDITOR },
      { id: 4, spaceName: '只能看的团队', currentRole: SPACE_ROLE.VIEWER }
    ]
  }
  assert.deepEqual(copyTargets(groups, null).map((target) => target.id), [1, 2, 3])
  assert.deepEqual(copyTargets(groups, '1').map((target) => target.id), [2, 3])
  assert.equal(copyTargets(groups, 2)[0].kind, '私有空间')
  assert.deepEqual(copyTargets(undefined, null), [])
})
