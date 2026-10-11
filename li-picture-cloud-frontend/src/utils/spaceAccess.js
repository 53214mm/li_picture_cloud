import { SPACE_ROLE, SPACE_TYPE } from '../constants/space.js'

export function normalizePermissions(permissions) {
  const values = permissions instanceof Set
    ? [...permissions]
    : (Array.isArray(permissions) ? permissions : [])
  return [...new Set(values)]
}

export function hasPermission(permissions, permission) {
  return normalizePermissions(permissions).includes(permission)
}

export function collaborationMode(permissions, isTeamSpace) {
  if (!isTeamSpace || !hasPermission(permissions, 'collaboration:join')) return null
  return hasPermission(permissions, 'collaboration:edit') ? 'edit' : 'view'
}

export function groupMySpaces(ownedSpaces = [], memberships = [], currentUserId) {
  const privateSpaces = ownedSpaces.filter((space) => space.spaceType === SPACE_TYPE.PRIVATE)
  const ownedTeamSpaces = ownedSpaces
    .filter((space) => space.spaceType === SPACE_TYPE.TEAM)
    .map((space) => ({ ...space, currentRole: SPACE_ROLE.ADMIN }))
  const ownedIds = new Set(ownedSpaces.map((space) => String(space.id)))
  const joinedById = new Map()

  for (const membership of memberships) {
    const space = membership?.space
    if (!space || space.spaceType !== SPACE_TYPE.TEAM) continue
    const id = String(space.id)
    if (ownedIds.has(id) || String(space.userId) === String(currentUserId) || joinedById.has(id)) continue
    joinedById.set(id, { ...space, currentRole: membership.spaceRole })
  }

  return {
    privateSpaces,
    ownedTeamSpaces,
    joinedTeamSpaces: [...joinedById.values()]
  }
}

/**
 * 可以把图片添加进去的空间：自己的私有空间、自己的团队空间，以及自己是管理员或编辑者的团队空间。
 * 图片当前所在的空间不算。
 */
export function copyTargets(groups, currentSpaceId) {
  const writable = new Set([SPACE_ROLE.ADMIN, SPACE_ROLE.EDITOR])
  const targets = [
    ...(groups?.privateSpaces || []).map((space) => ({ id: space.id, name: space.spaceName, kind: '私有空间' })),
    ...(groups?.ownedTeamSpaces || []).map((space) => ({ id: space.id, name: space.spaceName, kind: '团队空间' })),
    ...(groups?.joinedTeamSpaces || []).filter((space) => writable.has(space.currentRole))
      .map((space) => ({ id: space.id, name: space.spaceName, kind: '团队空间' }))
  ]
  return targets.filter((target) => currentSpaceId == null || String(target.id) !== String(currentSpaceId))
}

export function buildSpaceCreatePayload(form) {
  const name = form.spaceName?.trim()
  return {
    ...(name ? { spaceName: name } : {}),
    spaceLevel: form.spaceLevel,
    spaceType: form.spaceType
  }
}

/** Build the user-scoped query used by gallery analysis. */
export function buildMySpaceQuery(userId) {
  return { current: 1, pageSize: 20, userId }
}
