// One pending timeout, no background catch-up and no per-frame rAF loop.
export function createSpriteClock({ durations, onFrame, schedule = setTimeout, cancel = clearTimeout }) {
  let timer = null
  let frame = 0
  let playing = false
  let disposed = false
  let generation = 0

  function next() {
    const cycle = generation
    timer = schedule(() => {
      if (disposed || !playing || cycle !== generation) return
      timer = null
      frame = (frame + 1) % durations.length
      onFrame(frame)
      next()
    }, durations[frame])
  }

  function stop() {
    generation += 1
    if (timer !== null) cancel(timer)
    timer = null
    frame = 0
    onFrame(0)
  }

  return {
    setPlaying(value) {
      if (disposed || playing === value) return
      playing = value
      if (playing) next()
      else stop()
    },
    destroy() {
      disposed = true
      playing = false
      stop()
    }
  }
}
