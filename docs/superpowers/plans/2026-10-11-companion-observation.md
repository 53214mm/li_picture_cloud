> 2026-10-11 correction: the historical SAFE_LAYOUT rectangle-only visual plan below is superseded by docs/plans/2026-10-11-companion-real-visual-capture.md and the current observation design. Rectangles never reach a paid model. Real full-image pixels and permitted labels are captured in a separate disclosed region; unsupported cases skip vision without charging. Text observation remains applicable.

# Companion Observation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans; root implements, independent read-only reviewer checks design and final result. Existing user authorization supersedes additional approval prompts and routine git commits; do not commit/push.

**Goal:** One independent foreground observation loop with authorized context, one decision, safe optional layout vision, and explainable records.
**Architecture:** Add an observation module alongside legacy event debugging. Browser operations update data only; server owner-bound snapshots and persistent admission guards control every BYOK dispatch.
**Tech Stack:** Existing Java21/Spring/MyBatis/Liquibase/Vue3/Pinia/Node/Playwright, no new dependencies.
**Spec:** ../specs/2026-10-11-companion-observation-design.md

## Global Constraints

Auto/vision default off. Explicit session choice text1..500/vision0..100, 6/2 unenabled suggestions only; no silent clamp/default fallback. Grant binds limits, provider/model and expiry. Exhaustion pauses until explicit renewal; visible engineering daily hard limits1000/200 persist across tabs/refresh. MANUAL text100 existing counter is not auto authorization. Default90s, clamp90–600s, LOW/NORMAL speech300/120s. Text160tokens/8s/1024chars. Site enum+IDs+bounded geometry only, no arbitrary captured pixels. Owner/account binding, late result revoke, no raw logs/persistence/private reasoning. Implement in existing isolation, protect original dirty tree and private config, no real models or production actions.

## Review Focus

Refresh/reopening and two tabs must not erase persistent quota or lease; failure/cancel after dispatch still consumes.
Chat starts between preparation/dispatch/return: no stale bubble or old raw trace reappears after revoke.
Picture/memory permission changes during request: exact dependency recheck; a harmless click retains response.
Untrusted layout/token/provider content never grants permissions or transmits user pixels/credentials.
EOF/zero body/partial output and cached visual expiry remain distinct from valid SILENT.

### Task 1: Context and server-bound snapshots
Files: new application/companion/observation/ObservationContracts.java, ObservationContext.java, ObservationPageResolver.java, SafeLayoutPreview.java; modify CompanionMemoryRecall.java for recallLocally; tests ObservationContextTest/SafeLayoutPreviewTest.
Interfaces: prepare(owner, page refs/rects) -> immutable owner-bound Snapshot; recallLocally(...) -> existing MemoryRecall without embeddings. Picture authorization is server PICTURE_VIEW, recent chat current segment max6, related memories max2.
- [ ] Write/run failing owner, no-vector, authorized refs, zero user-pixels, bounds tests.
- [ ] Implement snapshots/context and canonical safe PNG.
- [ ] Run related tests and record result/rulings.

### Task 2: Admission, singleflight, budgets
Files: new ObservationQuota.java, mapper/CompanionObservationUsageMapper.java, infrastructure/persistence/companion/MybatisObservationQuota.java, Liquibase 2026-10-11-companion-observation.xml; modify master XML; tests MybatisObservationQuotaTest.
Interfaces: status(owner,date), claim(owner,date,kind,limits,now,token), reserveVision(...), release(owner,date,token); immutable counters and stable gate reasons.
- [ ] Red concurrency/reopen/manual-vs-auto/expiry/cancel-no-refund tests.
- [ ] Implement atomic per-owner day row and bounded lease; no model in transaction.
- [ ] Verify real H2 mapper concurrency/rollback and source migration contract.

### Task 3: One decision and optional BYOK layout caption
Files: new CompanionObservationService.java, ObservationDecision.java, ObservationVisionClient.java, ObservationTrace.java, controller/CompanionObservationController.java; modify VisionRouter.java readonly inspection; tests CompanionObservationServiceTest/ObservationVisionClientTest.
Interfaces: open/prepare/observe/close/status, exact provider turns/metadata; decision action/emote/text plus optional next interval/focus/topic.
- [ ] Red no-click snapshot decision, gates, intensity, empty/partial, redaction, owner token, permission/chat versions, vision-off/cache/failed-vision/limits tests.
- [ ] Implement route BYOK only, exactly one text dispatch and at most one optional bounded vision caption; reuse safe SSE metadata.
- [ ] Mock HTTP assert outgoing messages/160/safePNG/no tools/no extra calls and terminal classifications.

### Task 4: Foreground coordinator and revoke boundaries
Files: new presentation/companionObserver.js, companionObserverTransport.js, observationTrace.js, safeVisualLayout.js, stores/companionObserver.js, composables/useCompanionObserver.js, api/companionObservation.js; tests companionObserver.test.mjs.
Interfaces: owner/configure/update/suppress/observeNow/clear/dispose; immutable50-row trace journal; DOM collector yields only refs and bounds. Timer injectable, 90s default, silence backoff.
- [ ] Red fake-clock no-click speaking/dense-click no call/no cancel, account/chat/object changes, hidden/pause, error stop/budget, no resurrection and no persist tests.
- [ ] Implement coordinator/store/transport, conditional safe preview only when vision requested.
- [ ] Verify focused scheduler and real transport request binding tests.

### Task 5: Controls, real page material, explanatory window
Files: new components/companion/CompanionObservationPanel.vue; modify AppLayout.vue, CompanionRehearsalView.vue, GalleryView.vue, PictureDetailView.vue, PictureList.vue. Update actual SFC fixtures as needed; tests companionObservationPanel.test.mjs/sitePresenceShell.test.mjs.
- [ ] Red actual rendered default-off/disclosure/safe preview/sensitive skip/expand trace/normal chat priority/legacy label tests.
- [ ] Integrate shell-owned observer, whitelist refs/material on pages, shared debug panel; old buttons clearly protocol simulation/manual debugging.
- [ ] Run actual local Edge fixture through no-click/dense-click/chat/route/account/vision/budgets/empty matrix; inspect viewport.

### Task 6: Review, local delivery and restoration
Files: STATE.md, DECISIONS.md, docs/work/LINGYE-INDEPENDENT-OBSERVATION.md; workspace manifests/patch/recovery and Library archival.
- [ ] Fresh independent review, each material finding red→green.
- [ ] Full offline mvn verify, node tests, strict lint, UI false/true builds and bundle checks; inspect exact counts.
- [ ] Source/private-config/old patch/counter/port guards, apply incremental once, compile bytecode comparison, authorized local Java/Vite restart, no DB rebuild/real models.
- [ ] Local health/401/JMX/default-off flags/browser mocks; source tree and four-old+new patch hash/order; safe new Library copy with full integrity verification.
