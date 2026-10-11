/** Preserve exact positive Java Long IDs; never stringify an already-rounded number. */
export function exactEntityId(value) {
  if (typeof value === 'number' && (!Number.isSafeInteger(value) || value <= 0)) return null
  if (typeof value !== 'number' && typeof value !== 'string') return null
  const id = String(value)
  return /^[1-9]\d{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n ? id : null
}
