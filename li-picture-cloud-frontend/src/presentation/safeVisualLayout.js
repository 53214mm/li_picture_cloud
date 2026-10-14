const PAGES = { gallery: 'GALLERY', 'picture-detail': 'PICTURE_DETAIL', companion: 'COMPANION', 'companion-rehearsal': 'COMPANION', 'picture-upload': 'UPLOAD' }
function cssVisible(node, windowRef) {
  try {
    if (typeof node.checkVisibility === 'function') return node.checkVisibility({ opacityProperty: true, visibilityProperty: true, contentVisibilityAuto: true })
    if (typeof windowRef.getComputedStyle !== 'function') return false
    for (let current = node, depth = 0; current && depth < 64; current = current.parentElement, depth++) {
      const style = windowRef.getComputedStyle(current)
      if (style.display === 'none' || ['hidden', 'collapse'].includes(style.visibility) || style.contentVisibility === 'hidden' || Number(style.opacity) === 0) return false
    }
    return true
  } catch { return false }
}
/** Numeric whitelist layout only; never reads text, input values, pixels, URL, canvas or screenshots. */
export function collectSafeLayout(routeName, vision, documentRef = document, windowRef = window) {
  const page = PAGES[routeName] || 'UNKNOWN'
  if (page === 'UNKNOWN' || documentRef.hidden) return { page, pictureIds: [], rects: [], dependency: '' }
  const cards = ['GALLERY', 'PICTURE_DETAIL'].includes(page) ? [...documentRef.querySelectorAll('[data-observation-picture]')].filter(node => {
    const rect = node.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0 && rect.left < windowRef.innerWidth && rect.top < windowRef.innerHeight && !node.closest('dialog:not([open]),[inert],[hidden]') && cssVisible(node, windowRef)
  }).slice(0, 6) : []
  const validated = cards.filter(node => /^\d{1,19}$/.test(node.dataset.observationPicture) && BigInt(node.dataset.observationPicture) > 0n && BigInt(node.dataset.observationPicture) <= 9223372036854775807n)
  const valid = [...new Map(validated.map(node => [BigInt(node.dataset.observationPicture).toString(), node])).values()]
  // Existing snowflake IDs may exceed JS safe integers: leave them as decimal strings for Jackson Long.
  const ids = valid.map(node => BigInt(node.dataset.observationPicture).toString())
  const rects = vision && validated.length === cards.length ? valid.map(node => {
    const r = node.getBoundingClientRect(); const x = Math.max(0, Math.min(639, Math.floor(r.left / windowRef.innerWidth * 640)))
    const y = Math.max(0, Math.min(359, Math.floor(r.top / windowRef.innerHeight * 360)))
    return { x, y, width: Math.max(1, Math.min(640 - x, Math.ceil(Math.min(r.right, windowRef.innerWidth) / windowRef.innerWidth * 640) - x)), height: Math.max(1, Math.min(360 - y, Math.ceil(Math.min(r.bottom, windowRef.innerHeight) / windowRef.innerHeight * 360) - y)) }
  }) : []
  return { page, pictureIds: ids, rects, dependency: `${validated.length === cards.length ? 'VALID' : 'INVALID'}|${valid.map(node => `${BigInt(node.dataset.observationPicture).toString()}:${node.dataset.observationVersion || ''}`).join('|')}` }
}
