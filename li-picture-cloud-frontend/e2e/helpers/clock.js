export async function freezeClock(page) {
  // Start before the pause target so host/browser scheduling cannot put it in
  // the past. Install before navigation; no application timers run in this gap.
  await page.clock.install({ time: new Date('2026-01-01T00:00:00Z') })
  await page.clock.pauseAt(new Date('2026-01-01T01:00:00Z'))
}
