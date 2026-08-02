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
