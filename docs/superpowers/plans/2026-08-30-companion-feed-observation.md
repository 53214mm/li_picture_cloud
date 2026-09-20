# Companion Feed Observation Implementation Plan

> **For agentic workers:** This plan is executed inline in the current workspace after explicit user approval. The repository contains unrelated uncommitted companion-module changes; preserve them and review only this feature's files.

**Goal:** Add an admin-only "喂养观测" read model that explains a companion feeding run through run creation, picture authorization, nutrition analysis, and growth settlement without introducing a general logging platform or event table.

**Architecture:** Reuse `companion_feed_run` as the execution record and `companion_growth_record` as the successful growth fact. Add a focused read-only MyBatis projection, a pure stage-summary assembler, administrator endpoints, and two Vue pages. Stage positions are derived from existing terminal statuses and safe error codes; no new domain state transitions are introduced.

**Tech Stack:** Java 21, Spring Boot MVC, MyBatis-Plus, Liquibase, MySQL/H2 tests, Vue 3, Vue Router, Axios, Node test runner.

**Spec:** `docs/superpowers/specs/2026-08-13-companion-vision-foundation-design.md`, plus the approved companion-feed-observation discussion in this task.

## Global Constraints

- Do not add ELK, OpenTelemetry, a message queue, or another operational logging infrastructure.
- Do not add a trace-event table in this iteration.
- Do not persist request bodies, image URLs/bytes, model raw responses, tokens, cookies, or stack traces.
- Protect every observation endpoint with `@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)`.
- Keep `FeedingRunRepository` write-model methods and feeding transaction/idempotency semantics unchanged.
- Cap observation pages at 50 rows and use a fixed `createTime DESC, id DESC` order.
- Preserve all existing uncommitted work unrelated to this feature.

## Functional Units

### 1. Stage vocabulary and readable summaries

Create a pure assembler and view records under `application/companion/observation`. Define the four displayed stages (`RUN_CREATED`, `AUTHORIZATION`, `NUTRITION`, `SETTLEMENT`) and the display states `SUCCESS`, `FAILED`, `DEGRADED`, `SKIPPED`, `PROCESSING`, and `UNKNOWN`. Map safe codes to short Chinese explanations. Treat a completed run with retained error fields as “最终成功，之前重试失败过”. Keep technical IDs and safe codes in a separate detail projection.

Test success, fallback, familiar-image shortcut, rejected authorization, authorization failure, nutrition failure, settlement failure, processing, and retry-success history before implementing the assembler.

### 2. Read projection and migration indexes

Add a `CompanionFeedObservationRow` projection, `CompanionFeedObservationMapper`, XML queries, and `CompanionFeedObservationService`. The list/detail SQL may join `companion_feed_run`, `companion_growth_record`, and `user`; it must not join sharded `picture` in the page query. The service may load current picture metadata separately for a detail response and must label missing/deleted pictures as unavailable.

Add a Liquibase change set with indexes `(createTime, id)`, `(status, createTime, id)`, and `(correlationId)` plus rollback. Validate pagination, fixed ordering, filters, enum validation, page-size cap, and no sensitive columns in the projection.

### 3. Administrator REST contract

Add `AdminCompanionFeedObservationController` with:

```text
POST /api/admin/companion/feed-runs/page
GET  /api/admin/companion/feed-runs/{runId}
```

Return list summaries and detail timelines, growth/provenance data, retry history summary, technical IDs, and safe errors. Test admin annotation, ordinary-user rejection, page delegation, not-found behavior, and invalid query handling.

### 4. Structured runtime log supplementation

Add compact success, replay, and settlement-failure logs in `CompanionLifeService`, retaining the existing safe-field policy. Log only run/correlation/subject/picture IDs, attempt count, stage, actual mode, and exception type where needed. Do not make the backend page dependent on text logs.

### 5. Admin list and detail UI

Add `companionObservation.js`, the list page `/admin/companion-feed-runs`, the detail page `/admin/companion-feed-runs/:id`, and a reusable stage timeline component. Add the navigation entry for administrators. The default view uses plain Chinese summaries; a collapsible technical section exposes IDs, versions, idempotency key, revision, and safe error code. Make the list readable on mobile without horizontal scrolling.

### 6. Verification and handoff

Run focused backend tests, companion regression tests, Liquibase/migration checks, full backend verification, frontend tests/lint/build/bundle checks, `git diff --check`, and a feature-only diff review. Manually verify success, fallback, authorization rejection, nutrition failure, settlement rollback, processing, retry-success, correlation search, and admin authorization.
