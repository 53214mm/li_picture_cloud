// Browser rendering budget only. Domain presentation, reduced motion, visibility
// and user pause remain separate inputs to the existing sprite player.
const labels = Object.freeze({
  unmeasured: '静态立绘',
  compact: '轻量显示',
  'save-data': '节省流量',
  'slow-connection': '静态立绘',
  full: '常规显示'
})

/**
 * Unknown widths use a conservative compact budget until a real measurement.
 * Browser width is a positive finite number; do not coerce strings/booleans.
 * Only explicit network restrictions reduce an otherwise eligible desktop.
 * Priority: unmeasured > compact > save-data > slow-connection > full.
 */
export function mapCompanionRenderPolicy(input = {}) {
  const signals = input && typeof input === 'object' && !Array.isArray(input) ? input : {}
  const measured = typeof signals.viewportWidth === 'number' && Number.isFinite(signals.viewportWidth) && signals.viewportWidth > 0
  const compact = !measured || signals.viewportWidth < 768
  const reason = !measured ? 'unmeasured'
    : compact ? 'compact'
      : signals.saveData === true ? 'save-data'
        : signals.effectiveType === 'slow-2g' || signals.effectiveType === '2g' ? 'slow-connection' : 'full'
  return Object.freeze({ compact, allowMotion: reason === 'full', reason, label: labels[reason] })
}
