// Only a server-authorized PNG is painted. Surrounding DOM, forms and desktop are never read.
export async function captureObservationRegion(png, { documentRef = document, ImageCtor = Image, signal } = {}) {
  const current = () => { if (signal?.aborted) throw new Error('CAPTURE_CANCELLED') }
  current()
  if (typeof png !== 'string' || png.length > 1048576 || !/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(png)) throw new Error('INVALID_CAPTURE_SOURCE')
  const image = new ImageCtor()
  await new Promise((resolve, reject) => {
    const cleanup = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); image.onload = image.onerror = null }
    const abort = () => { cleanup(); reject(new Error('CAPTURE_CANCELLED')) }
    const timer = setTimeout(() => { cleanup(); reject(new Error('CAPTURE_TIMEOUT')) }, 5000)
    image.onload = () => { cleanup(); resolve() }
    image.onerror = () => { cleanup(); reject(new Error('CAPTURE_IMAGE_UNAVAILABLE')) }
    signal?.addEventListener('abort', abort, { once: true })
    image.src = png
  })
  current()
  if (image.naturalWidth !== 640 || image.naturalHeight !== 360) throw new Error('INVALID_CAPTURE_DIMENSIONS')
  const canvas = documentRef.createElement('canvas'); canvas.width = 640; canvas.height = 360
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) throw new Error('CAPTURE_UNAVAILABLE')
  context.drawImage(image, 0, 0)
  // Digest the actual browser pixels, then export the isolated canvas, before model dispatch.
  const pixels = context.getImageData(0, 0, 640, 360).data
  const digest = [...new Uint8Array(await crypto.subtle.digest('SHA-256', pixels))].map(value => value.toString(16).padStart(2, '0')).join('')
  current()
  const blob = await new Promise((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('CAPTURE_UNAVAILABLE')), 'image/png'))
  const captured = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error('CAPTURE_UNAVAILABLE')); reader.readAsDataURL(blob) })
  current()
  if (captured.length > 1048576) throw new Error('CAPTURE_TOO_LARGE')
  return { png: captured, pixelDigest: digest }
}
