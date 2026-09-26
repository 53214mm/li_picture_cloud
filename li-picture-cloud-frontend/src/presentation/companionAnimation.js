// R07 consumes only the R06 protocol. These are rendering choices, not domain states.
const activities = new Set(['idle', 'feeding', 'thinking', 'responding', 'acknowledging'])

export function mapCompanionAnimation(presentation) {
  const p = presentation
  const supported = p?.version === 1 && p.availability === 'ready' && !!p.companionId &&
    p.appearance?.assetKey === 'lingye-adult-v1' && p.appearance.visualStage === 'adult' &&
    p.appearance.allowIdle === true && activities.has(p.activity)
  return Object.freeze({
    companionId: p?.companionId ?? null,
    state: !supported ? 'static' : p.activity !== 'idle' ? 'focus' : p.attention === 'proposal' ? 'attention' : 'idle'
  })
}

// One two-column atlas; frame is an atlas index, offsetY is a tiny sprite pose.
// Every loop begins neutral. No CSS animation clock, event queue or success cue.
const clips = {
  idle: [{ frame: 0, ms: 4200 }, { frame: 1, ms: 140 }],
  focus: [{ frame: 0, ms: 1600 }, { frame: 0, offsetY: 2, ms: 220 }, { frame: 1, offsetY: 2, ms: 140 }, { frame: 0, ms: 1800 }],
  attention: [{ frame: 0, ms: 600 }, { frame: 1, ms: 140 }, { frame: 0, ms: 180 }, { frame: 1, ms: 140 }],
  greeting: [{ frame: 0, ms: 160 }, { frame: 1, ms: 140 }, { frame: 0, ms: 160 }]
}

/** One controller per mounted Home player. Only one pending timeout at any time. */
export function createCompanionAnimator({ onChange, schedule = setTimeout, cancel = clearTimeout }) {
  let timer = null
  let generation = 0
  let disposed = false
  let state = 'static'
  let step = 0
  let identity = null
  let attentionConsumed = false
  let lastInteraction = 0

  function publish() {
    const pose = clips[state]?.[step]
    onChange(Object.freeze({ state, frame: pose?.frame ?? 0, offsetY: pose?.offsetY ?? 0, playing: state !== 'static' }))
  }
  function stop() {
    generation += 1
    if (timer !== null) cancel(timer)
    timer = null
  }
  function next() {
    const cycle = generation
    timer = schedule(() => {
      if (disposed || cycle !== generation) return
      timer = null
      step += 1
      if (step === clips[state].length) {
        step = 0
        if (state === 'attention' || state === 'greeting') state = 'idle'
      }
      publish()
      next()
    }, clips[state][step].ms)
  }
  function start(target) {
    stop()
    state = target
    step = 0
    if (state === 'attention') attentionConsumed = true
    publish()
    if (state !== 'static') next()
  }

  return {
    update(presentation, playable, interactionRequest = 0) {
      if (disposed) return
      const intent = mapCompanionAnimation(presentation)
      const direct = Number.isSafeInteger(interactionRequest) && interactionRequest > lastInteraction
      lastInteraction = interactionRequest
      const changedIdentity = intent.companionId !== identity
      if (changedIdentity) {
        identity = intent.companionId
        attentionConsumed = false
      }
      let target = playable ? intent.state : 'static'
      // Ephemeral input has lower priority than R06 facts. Never defer it while
      // hidden, busy, reduced-motion or showing a proposal; never reset its quota.
      if (target === 'idle' && (direct || state === 'greeting' && !changedIdentity)) target = 'greeting'
      if (target === 'attention' && attentionConsumed && state !== 'attention') target = 'idle'
      if (changedIdentity || target !== state) start(target)
    },
    destroy() {
      if (disposed) return
      disposed = true
      stop()
    }
  }
}
