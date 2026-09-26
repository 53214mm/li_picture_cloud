export const PICTURE_DRAG_TYPE = 'application/x-lipicturecloud-companion-picture'

export function normalizePictureId(value) {
  if (typeof value === 'number' && (!Number.isSafeInteger(value) || value <= 0)) return null
  if (typeof value !== 'number' && typeof value !== 'string') return null
  const id = String(value)
  return /^[1-9]\d{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n ? id : null
}

// DataTransfer contains an opaque ticket, never a URL, user text or trusted ID.
export function createPictureDragSession(tokenFactory = () => globalThis.crypto.randomUUID()) {
  let current = null
  return {
    start(pictureId, userId) {
      current = null
      const id = normalizePictureId(pictureId)
      const actor = normalizePictureId(userId)
      if (!id || !actor) return null
      current = { id, actor, token: tokenFactory() }
      return current.token
    },
    accepts(types, userId) {
      return !!current && current.actor === normalizePictureId(userId) &&
        Array.from(types || []).includes(PICTURE_DRAG_TYPE) && !Array.from(types || []).includes('Files')
    },
    take(token, userId) {
      if (!current || current.token !== token || current.actor !== normalizePictureId(userId)) return null
      const id = current.id
      current = null
      return id
    },
    clear() { current = null }
  }
}

// Deliberately narrower than server PICTURE_VIEW: match the existing private
// owner picker. This is entry-point policy, not an authorization substitute.
export async function inspectCompanionPicture(pictureId, userId, { readPicture, readSpace }) {
  const id = normalizePictureId(pictureId)
  const actor = normalizePictureId(userId)
  if (!id || !actor) throw new Error('invalid-candidate')
  const picture = await readPicture(id)
  const spaceId = normalizePictureId(picture?.spaceId)
  if (normalizePictureId(picture?.id) !== id || !spaceId) throw new Error('unsupported-picture')
  const space = await readSpace(spaceId)
  if (normalizePictureId(space?.id) !== spaceId || space.spaceType !== 0 || normalizePictureId(space.userId) !== actor) throw new Error('unsupported-space')
  return { picture, space }
}

export function createPictureInspector({ readPicture, readSpace, onChange }) {
  let generation = 0
  return {
    async inspect(id, actor) {
      const cycle = ++generation
      onChange({ phase: 'loading' })
      try {
        const candidate = await inspectCompanionPicture(id, actor, { readPicture, readSpace })
        if (cycle === generation) onChange({ phase: 'ready', ...candidate })
      } catch {
        if (cycle === generation) onChange({ phase: 'error' })
      }
    },
    clear() { generation += 1; onChange(null) }
  }
}
