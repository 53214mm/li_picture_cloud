const existingStages = new Set(['LIGHT', 'SEEDLING', 'COMPANION'])

// Presentation only: the legacy stage remains authoritative in every business panel.
// Missing/unknown stages use the same neutral adult artwork, without animation.
export function resolveCompanionVisual(currentStage) {
  return {
    assetKey: 'lingye-adult-v1',
    visualStage: 'adult',
    currentStage,
    allowIdle: existingStages.has(currentStage)
  }
}
