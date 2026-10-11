// The shipped adult artwork is a static placeholder for every growth stage.
// Its atlas contains artificial blinking in every clip; do not enable it until
// replacement poses have been reviewed. Business stages remain authoritative.
export function resolveCompanionVisual(currentStage) {
  return {
    assetKey: 'lingye-adult-v1',
    visualStage: 'adult',
    currentStage,
    allowIdle: false
  }
}
