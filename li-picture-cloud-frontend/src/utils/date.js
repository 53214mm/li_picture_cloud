// API dates may be ISO strings or epoch milliseconds (including legacy cache
// payloads serialized as strings). Keep the existing browser-local date display.
export function formatDate(input) {
  let value = input
  if (typeof value === 'string') {
    value = value.trim()
    if (!value) return '-'
    if (/^[+-]?\d+$/.test(value)) value = Number(value)
  } else if (typeof value !== 'number' && !(value instanceof Date)) return '-'

  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? date.toLocaleDateString('zh-CN') : '-'
}
