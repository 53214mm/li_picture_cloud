import { observationRecord, observationAttempt } from './observationTrace.js'
import { captureObservationRegion } from './realObservationCapture.js'
const RANGE = { text: 500, vision: 100 }
// The region uses original-image pixels in a fixed layout, not page rectangle positions.
const dependency = page => JSON.stringify([page?.page, page?.pictureIds || [], page?.dependency || ''])
export function createCompanionObserver({ api, onChange = () => {}, collect = () => ({ page: 'COMPANION', pictureIds: [], rects: [] }), capture = captureObservationRegion, now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout }) {
  let owner = null, generation = 0, timer = null, previewTimer = null, controller = null, previewController = null, blocked = false, sequence = 0, previewSequence = 0, session = null, disposed = false
  let material = collect(false), materialEpoch = 0, held = false, cadence = 90, attempt = 0, singlePending = false
  let controlSequence = 0, controlPending = false, controlQueue = Promise.resolve(), serverBusy = false, requestStage = null, completedSequence = 0
  let value = { enabled: false, phase: 'off', page: material.page, held: false, status: null, statusPhase: 'idle', suppressed: false, reason: null, presentation: null, records: [], speeches: [], speechVisible: true, preview: null, visualSkip: 'VISION_OFF', textRemaining: 0, visionRemaining: 0, nextAt: null, options: null }
  const emit = patch => { value = { ...value, ...patch }; onChange(value); return value }
  const cancelTimer = () => { if (timer != null) clearTimer(timer); timer = null }
  function showPreview(prepared) {
    if (previewTimer != null) clearTimer(previewTimer)
    const safe = typeof prepared.preview === 'string' && prepared.preview.length <= 1048576 && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(prepared.preview)
    emit({ preview: safe ? prepared.preview : null, visualSkip: prepared.visualSkip || (prepared.preview && !safe ? 'INVALID_PREVIEW' : null) })
    if (safe) { const cycle = generation; const id = previewSequence; previewTimer = setTimer(() => { if (cycle === generation && id === previewSequence) emit({ preview: null, visualSkip: 'PREVIEW_EXPIRED' }); previewTimer = null }, 30000) }
  }
  function revoke(reason, clear = false, preservePresentation = false) {
    singlePending = false
    controlPending = false; serverBusy = false; controlQueue = Promise.resolve(); requestStage = null
    generation++; cancelTimer(); controller?.abort(); controller = null
    previewController?.abort(); previewController = null
    if (previewTimer != null) clearTimer(previewTimer); previewTimer = null
    if (session) Promise.resolve(api.close(session.sessionId)).catch(() => {})
    session = null
    emit({ enabled: false, phase: reason === 'OFF' ? 'off' : 'paused', reason, statusPhase: value.status ? 'ready' : 'idle', presentation: preservePresentation ? value.presentation : null, preview: null, nextAt: null, ...(clear ? { records: [], speeches: [], speechVisible: true, status: null, statusPhase: 'idle', options: null, textRemaining: 0, visionRemaining: 0 } : {}) })
  }
  const sameOwner = cycle => !disposed && owner != null && cycle === generation
  const alive = cycle => sameOwner(cycle) && !blocked
  function currentRound(cycle, round) {
    if (disposed || owner == null || cycle !== generation || round !== session) return false
    const expiry = Date.parse(round.expiresAt)
    if (Number.isFinite(expiry) && now() >= expiry) { revoke('SESSION_EXPIRED'); return false }
    return true
  }
  const waitingPhase = () => held ? 'held' : value.page === 'UNKNOWN' ? 'page-skipped' : 'waiting'
  function schedule(seconds = cadence) {
    cadence = Math.max(90, Math.min(600, Number(seconds) || 90))
    cancelTimer(); if (!value.enabled || !session || !value.options.auto && !singlePending) return
    const delay = cadence * 1000
    emit({ phase: blocked ? 'suspended' : controlPending || controller ? 'draining' : waitingPhase(), nextAt: now() + delay })
    if (blocked || controlPending || controller) return
    timer = setTimer(() => { timer = null; void observeNow() }, delay)
  }
  function resumeSchedule() {
    if (blocked || controlPending || controller || !session || !value.enabled) return
    if (!currentRound(generation, session)) return
    if (!value.options.auto && !singlePending) return
    const deadline = value.nextAt > now() ? value.nextAt : now() + cadence * 1000
    cancelTimer(); emit({ phase: waitingPhase(), nextAt: deadline })
    timer = setTimer(() => { timer = null; void observeNow() }, deadline - now())
  }
  function syncControl(suspended) {
    const round = session, cycle = generation, sequence = ++controlSequence
    if (!round) return
    controlPending = true
    controlQueue = controlQueue.catch(() => {}).then(async () => {
      if (!currentRound(cycle, round)) return
      try {
        const response = await api.control(round.sessionId, { sequence, suspended })
        if (!currentRound(cycle, round) || sequence !== controlSequence) return
        if (response?.sequence !== sequence || response?.suspended !== suspended || typeof response.busy !== 'boolean') throw new Error('CONTROL_BINDING_FAILED')
        if (response.expiresAt && Date.parse(response.expiresAt) !== Date.parse(round.expiresAt)) throw new Error('CONTROL_BINDING_FAILED')
        const completedAck = response.busy && Number.isInteger(response.inFlightSequence) && response.inFlightSequence <= completedSequence
        reconcile({ response }, completedAck); serverBusy = response.busy && !completedAck; controlPending = false
        const exhausted = value.textRemaining <= 0 || value.options.visionLimit > 0 && value.visionRemaining <= 0
        if (exhausted) { revoke('BUDGET_EXHAUSTED'); return }
        emit({ phase: blocked ? 'suspended' : serverBusy || controller ? 'draining' : waitingPhase(), reason: blocked ? 'OBSERVATION_SUSPENDED' : null })
        if (!blocked) {
          const serverDeadline = Date.parse(response.nextAt)
          if (Number.isFinite(serverDeadline) && serverDeadline > (value.nextAt || 0)) emit({ nextAt: serverDeadline })
          resumeSchedule()
        }
      } catch (error) {
        if (currentRound(cycle, round) && sequence === controlSequence) { const reason = safeFailure(error, 'control'); revoke(reason === 'OBSERVATION_FAILED' ? 'CONTROL_FAILED' : reason) }
      }
    })
  }
  async function refreshStatus() {
    if (owner == null || disposed) return
    const cycle = generation
    emit({ statusPhase: 'loading' })
    try { const status = await api.status(); if (cycle === generation && !disposed) emit({ status: { ...status, counts: mergeCounts(status.counts) }, statusPhase: 'ready' }) }
    catch { if (cycle === generation && !disposed) emit({ statusPhase: 'error', reason: 'STATUS_UNAVAILABLE' }) }
  }
  async function start(options) {
    if (!Number.isInteger(options.textLimit) || options.textLimit < 1 || options.textLimit > RANGE.text || !Number.isInteger(options.visionLimit) || options.visionLimit < 0 || options.visionLimit > RANGE.vision) throw new Error('BUDGET_OUT_OF_RANGE')
    if (!['LOW', 'NORMAL'].includes(options.intensity) || typeof options.auto !== 'boolean') throw new Error('INVALID_OPTIONS')
    if (owner == null || disposed || blocked || held) throw new Error('OBSERVER_SUPPRESSED')
    if (collect(false).page === 'UNKNOWN') throw new Error('SENSITIVE_OR_UNKNOWN_PAGE')
    const disclosed = value.status
    revoke('OFF'); const cycle = generation
    emit({ phase: 'opening', reason: null, options: { ...options } }); controller = new AbortController()
    try {
      const status = await api.status({ signal: controller.signal }); if (!sameOwner(cycle)) return
      emit({ status: { ...status, counts: mergeCounts(status.counts) }, statusPhase: 'ready' }); if (!status.enabled || !status.textReceiver || options.visionLimit > 0 && !status.visionReceiver) throw new Error('BYOK_ROUTE_UNAVAILABLE')
      if (disclosed && (disclosed.textReceiver?.token !== status.textReceiver.token || options.visionLimit > 0 && disclosed.visionReceiver?.token !== status.visionReceiver.token)) throw new Error('RECEIVER_CHANGED')
      // Receive the created id even after cancellation so a late grant can be closed.
      const opened = await api.open({ ...options, textReceiverToken: status.textReceiver.token, visionReceiverToken: options.visionLimit > 0 ? status.visionReceiver.token : null })
      if (!sameOwner(cycle)) { await api.close(opened.sessionId); return }
      session = opened; sequence = 0; controlSequence = 0; completedSequence = 0; controller = null; cadence = 90; material = collect(options.visionLimit > 0)
      if (!currentRound(cycle, opened)) return
      singlePending = blocked && !options.auto
      emit({ enabled: true, page: material.page, phase: blocked ? 'suspended' : held ? 'held' : material.page === 'UNKNOWN' ? 'page-skipped' : 'ready', reason: null, textRemaining: opened.textRemaining, visionRemaining: opened.visionRemaining })
      if (blocked) syncControl(true)
      else if (options.auto) schedule()
    } catch (error) { if (cycle === generation) { revoke('OPEN_FAILED'); throw error } }
  }
  function addAttempt(round, request, stage, reason, sent = false, outcome = null, visualSkip = null) {
    const row = observationAttempt({ id: `${round.sessionId}-attempt-${++attempt}`, request: request || { sessionId: round.sessionId }, at: now(), stage, reason, sent, outcome, visualSkip })
    emit({ records: [row, ...value.records].slice(0, 50), reason })
  }
  function mergeCounts(daily, completedAck = false) {
    let counts = value.status?.counts
    if (Number.isInteger(daily?.text) && Number.isInteger(daily.vision)) {
      const validDay = date => typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date)
      const dated = validDay(daily.usageDate) && validDay(counts?.usageDate)
      if (dated && daily.usageDate > counts.usageDate) counts = daily
      else if (dated && daily.usageDate === counts.usageDate) counts = { ...daily, text: Math.max(counts.text, daily.text), vision: Math.max(counts.vision, daily.vision) }
      else if (!dated && !completedAck) counts = daily
    }
    return counts
  }
  function reconcile(record, completedAck = false) {
    const response = record.response
    const remaining = key => Number.isInteger(response[key]) && response[key] >= 0 ? Math.min(value[key], response[key]) : value[key]
    emit({ textRemaining: remaining('textRemaining'), visionRemaining: remaining('visionRemaining'), status: { ...value.status, counts: mergeCounts(response.daily, completedAck) } })
  }
  function terminal(reason) {
    return ['REVOKED', 'CONTEXT_CHANGED', 'ROUTE_CHANGED', 'SESSION_UNAVAILABLE', 'SESSION_CLOSED_OR_EXPIRED', 'ACCESS_DENIED', 'AUTHENTICATION_REQUIRED', 'TEXT_DAY_LIMIT', 'VISION_DAY_LIMIT', 'BUDGET_EXHAUSTED', 'VISION_BUDGET_EXHAUSTED', 'EMPTY_OUTPUT', 'MODEL_FAILED', 'INVALID_OUTPUT', 'OBSERVATION_FAILED', 'PREPARE_FAILED', 'VISION_FAILED', 'OUTPUT_LIMIT', 'CAPTURE_PIXELS_UNCONFIRMED', 'RESPONSE_BINDING_FAILED'].includes(reason)
  }
  function safeFailure(error, stage) {
    if (error?.status === 401) return 'AUTHENTICATION_REQUIRED'
    if (error?.status === 403) return 'ACCESS_DENIED'
    const code = error?.message
    if (['REVOKED', 'SESSION_UNAVAILABLE', 'SESSION_CLOSED_OR_EXPIRED', 'ROUTE_CHANGED', 'CONTEXT_CHANGED', 'CHAT_BUSY', 'CHAT_CHANGED', 'OBSERVATION_SUSPENDED', 'SNAPSHOT_EXPIRED_OR_REPLAYED', 'CAPTURE_CANCELLED', 'CAPTURE_TIMEOUT', 'CAPTURE_IMAGE_UNAVAILABLE', 'INVALID_CAPTURE_SOURCE', 'INVALID_CAPTURE_DIMENSIONS', 'CAPTURE_UNAVAILABLE', 'CAPTURE_TOO_LARGE'].includes(code)) return code
    return stage === 'prepare' ? 'PREPARE_FAILED' : stage === 'capture' ? 'CAPTURE_UNAVAILABLE' : 'OBSERVATION_FAILED'
  }
  async function observeNow() {
    if (!value.enabled || blocked || controlPending || !session || controller || disposed) return
    if (serverBusy) { syncControl(false); return }
    cancelTimer(); const cycle = generation; const round = session; const options = value.options
    if (!currentRound(cycle, round)) return
    if (held || collect(false).page === 'UNKNOWN') { if (!options.auto) singlePending = true; emit({ phase: held ? 'held' : 'page-skipped', reason: held ? 'UI_OCCUPIED' : 'PAGE_UNSUPPORTED' }); schedule(); return }
    singlePending = false
    const pending = new AbortController(); controller = pending; const signal = pending.signal, epoch = materialEpoch
    let expected, stage = 'prepare', next = cadence
    const materialCurrent = latest => !blocked && epoch === materialEpoch && !held && dependency(latest) === dependency(collect(options.visionLimit > 0))
    try {
      const latest = collect(options.visionLimit > 0)
      material = latest
      const page = { page: latest.page, pictureIds: latest.pictureIds || [], rects: options.visionLimit > 0 ? latest.rects || [] : [] }
      emit({ phase: 'preparing' }); requestStage = 'prepare'
      const prepared = await api.prepare({ sessionId: round.sessionId, page }, { signal })
      if (!currentRound(cycle, round)) return
      if (!materialCurrent(latest)) { addAttempt(round, null, stage, 'MATERIAL_CHANGED'); return }
      let capturePixelDigest = null, captured = options.visionLimit > 0 ? prepared : { ...prepared, preview: null, visualSkip: 'VISION_OFF' }
      if (options.visionLimit > 0 && prepared.preview) {
        stage = 'capture'; requestStage = stage
        const pixels = await capture(prepared.preview, { signal })
        if (!currentRound(cycle, round)) return
        if (!materialCurrent(latest)) { addAttempt(round, null, stage, 'MATERIAL_CHANGED'); return }
        capturePixelDigest = pixels.pixelDigest; captured = { ...prepared, preview: pixels.png }
      }
      emit({ phase: 'observing' }); showPreview(captured)
      expected = { sessionId: round.sessionId, snapshotId: prepared.snapshotId, sequence: ++sequence, ...(capturePixelDigest ? { capturePixelDigest } : {}) }
      stage = 'observe'; requestStage = stage
      const response = await api.observe(expected, { signal })
      if (!currentRound(cycle, round)) return
      const record = observationRecord(expected, response, now())
      completedSequence = Math.max(completedSequence, expected.sequence)
      serverBusy = false
      reconcile(record); next = response.nextObservationSeconds || cadence
      if (!materialCurrent(latest)) {
        addAttempt(round, expected, stage, 'MATERIAL_CHANGED', true, record.response)
        if (value.textRemaining <= 0 || options.visionLimit > 0 && value.visionRemaining <= 0 || terminal(response.reason)) revoke(response.reason || 'BUDGET_EXHAUSTED', false, true)
        return
      }
      const parsed = record.response.parsed
      const visual = record.response.trace?.vision
      if (visual?.skip) emit({ visualSkip: `${visual.skip}${Number.isInteger(visual.httpStatus) && visual.httpStatus > 0 ? ` · HTTP ${visual.httpStatus}` : ''}` })
      const presentation = record.response.reason || record.response.modelDispatched === false || !parsed || parsed.action === 'SILENT' ? null : parsed
      const spoken = presentation?.action === 'SPEAK' && typeof presentation.text === 'string' && presentation.text.trim() ? Object.freeze({ id: `${round.sessionId}-${expected.sequence}`, at: record.at, text: presentation.text }) : null
      emit({ records: [record, ...value.records].slice(0, 50), speeches: spoken ? [spoken, ...value.speeches].slice(0, 50) : value.speeches, ...(spoken ? { speechVisible: true } : {}), reason: response.reason || null, presentation,
        phase: 'ready' })
      const stop = value.textRemaining <= 0 || options.visionLimit > 0 && value.visionRemaining <= 0 || terminal(response.reason)
      if (stop) { revoke(response.reason || 'BUDGET_EXHAUSTED', false, !!presentation); return }
    } catch (error) {
      if (currentRound(cycle, round)) {
        const reason = signal.aborted && epoch !== materialEpoch ? 'OBSERVATION_SUSPENDED' : safeFailure(error, stage)
        addAttempt(round, expected, stage, reason, stage === 'observe', null, stage === 'capture' ? reason : null)
        if (stage === 'capture') emit({ preview: null, visualSkip: reason })
        if (terminal(reason)) revoke(reason, false, true)
      }
    } finally {
      if (controller === pending) { controller = null; requestStage = null }
      if (currentRound(cycle, round) && value.enabled) schedule(next)
    }
  }
  async function previewOnly() {
    if (owner == null || disposed || value.enabled || blocked || held) return
    const cycle = generation, epoch = materialEpoch; const currentPreview = ++previewSequence; const latest = collect(true)
    if (latest.page === 'UNKNOWN') { emit({ preview: null, visualSkip: 'SENSITIVE_OR_UNKNOWN_PAGE' }); return }
    previewController?.abort(); const pending = new AbortController(); previewController = pending
    try {
      const prepared = await api.preview({ page: latest.page, pictureIds: latest.pictureIds || [], rects: latest.rects || [] }, { signal: pending.signal })
      if (!alive(cycle) || pending.signal.aborted || currentPreview !== previewSequence || epoch !== materialEpoch || dependency(latest) !== dependency(collect(true))) return
      const captured = prepared.preview ? { ...prepared, preview: (await capture(prepared.preview, { signal: pending.signal })).png } : prepared
      if (alive(cycle) && !pending.signal.aborted && currentPreview === previewSequence && epoch === materialEpoch && dependency(latest) === dependency(collect(true))) showPreview(captured)
    } catch { if (alive(cycle) && !pending.signal.aborted && currentPreview === previewSequence) emit({ preview: null, visualSkip: 'UNCONFIRMED_VISIBLE_REGION' }) }
    finally { if (previewController === pending) previewController = null }
  }
  function withdrawVisualConsent() {
    previewSequence++; previewController?.abort(); previewController = null
    if (previewTimer != null) clearTimer(previewTimer); previewTimer = null
    if (value.options?.visionLimit > 0 && (value.enabled || value.phase === 'opening')) revoke('VISUAL_CONSENT_WITHDRAWN')
    emit({ preview: null, visualSkip: 'VISION_OFF' })
  }
  return {
    state: () => value, refreshStatus, start, observeNow, previewOnly, withdrawVisualConsent,
    setOwner(id) { const next = id == null ? null : String(id); if (next === owner) return; revoke('OFF', true); owner = next; material = collect(false) },
    update(page) {
      if (!page) return
      emit({ page: page.page })
      if (dependency(page) !== dependency(material)) { materialEpoch++; previewSequence++; previewController?.abort(); emit({ preview: null, ...(value.enabled ? { phase: controller ? 'draining' : page.page === 'UNKNOWN' ? 'page-skipped' : held ? 'held' : 'waiting', reason: page.page === 'UNKNOWN' ? 'PAGE_UNSUPPORTED' : controller ? 'MATERIAL_CHANGED' : null } : {}) }) }
      material = page
    },
    hold(active) { if (!!active === held) return; held = !!active; materialEpoch++; previewSequence++; previewController?.abort(); emit({ held, preview: null, ...(value.enabled ? { phase: controller ? 'draining' : waitingPhase(), reason: held ? 'UI_OCCUPIED' : value.page === 'UNKNOWN' ? 'PAGE_UNSUPPORTED' : null } : {}) }) },
    suppress(active) {
      if (!!active === blocked) return
      blocked = !!active; emit({ suppressed: blocked })
      if (blocked) {
        materialEpoch++; previewSequence++; cancelTimer(); previewController?.abort(); previewController = null
        if (previewTimer != null) clearTimer(previewTimer); previewTimer = null
        if (requestStage && requestStage !== 'observe') controller?.abort()
        emit({ presentation: null, preview: null, ...(value.enabled ? { phase: 'suspended', reason: 'OBSERVATION_SUSPENDED' } : {}) })
        // Keep same-owner explicit opening intent; adopt its late grant already suspended.
      }
      if (session && value.enabled) syncControl(blocked)
    },
    dismissSpeech() { emit({ speechVisible: false }) },
    pause() { revoke('PAUSED') }, clear() { revoke('CLEARED', true) },
    dispose() { revoke('OFF', true); disposed = true }
  }
}

