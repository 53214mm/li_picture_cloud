/** One active page owns observations. Closed/replaced/account-reset leases are inert. */
export function createPresentationChannel(onChange) {
  let generation = 0
  function reset() {
    generation += 1
    onChange({ homeStatus: 'unobserved' })
  }
  function acquire() {
    reset()
    const captured = generation
    return {
      publish(snapshot) { if (captured === generation) onChange(snapshot) },
      close() { if (captured === generation) reset() }
    }
  }
  return { acquire, reset }
}
