// Only the explicit v3 visual-observation envelope changes attribution.
// Older narrative records retain their original presentation.
export function isObjectiveGrowthReason(reason) {
  return typeof reason === 'string' && reason.trimStart().startsWith('可见观察：')
}
