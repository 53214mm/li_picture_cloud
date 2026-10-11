# 绫页 bounded presence backend — 2026-10-09

## Scope and defaults

A separate authenticated presence API coexists with ordinary chat. It never writes synthetic turns into chat history, changes chat preferences, or uses ordinary-chat quota. No production deployment, real credentials, paid model calls or new provider were used in development.

Presence is off until the owner explicitly opens a session with `enabled:true`. The existing `app.companion.enabled=false` removes these endpoints. `app.companion.presence.enabled=false` additionally kills presence sessions; capability availability defaults true. Real BYOK is independently disabled by default (`app.companion.presence.real-enabled=false`), even if the user requests BYOK. Opening a DEMO session does not resolve credentials, route a model, reserve database quota or call an external provider.

Initial events convey only event type and page category. They contain **no picture pixels, observations, IDs, arbitrary text, DOM, URLs, form fields, user/owner IDs or credentials**. A picture-open event is not image understanding. Authorized server-side picture context is a later increment.

## API

All endpoints use existing `BaseResponse` envelopes and authenticated session identity. Body-supplied identity and other unknown fields are rejected with sanitized HTTP 400 responses.

- `POST /companion/presence/session`: `{enabled:true,mode:"DEMO"|"BYOK",maxDecisions:1..12,muted:false,intensity:"LOW"|"NORMAL"}`. Defaults: mode DEMO, six decisions, LOW. Returns `{sessionId,expiresAt,remainingDecisions,mode}`. Explicit false/missing enabled closes the owner's current session and returns OFF with null session ID. Reopening replaces the old session; sessions are memory-only, expire after 15 minutes, and restart closed.
- `POST /companion/presence/decide`: `{sessionId,eventId,sequence,observedAt,eventType,page}`. Event ID: 1–64 ASCII letters/digits/underscore/hyphen; sequence positive; observedAt ISO instant. Events: PAGE_ENTER, PICTURE_OPENED, UPLOAD_SUCCEEDED, USER_MESSAGE. Pages: COMPANION, GALLERY, PICTURE_DETAIL, UPLOAD. PICTURE_OPENED requires PICTURE_DETAIL; UPLOAD_SUCCEEDED requires UPLOAD.
- Response: `{sessionId,eventId,sequence,action,emote,text,reason,remainingDecisions,expiresAt,mode}`. Action: SILENT/EMOTE/SPEAK; emote null or WAVE/NOD/SPARKLE; speech plain short text. Clients must discard old-session/old-sequence replies and render text escaped.
- `DELETE /companion/presence/session/{sessionId}` closes only the owner's matching session; no cross-owner lookup.

## Boundaries and budgets

Events older than 30 seconds or more than five seconds into the future do not generate. Dedupe, monotonic sequence, 256-event session cap, NORMAL 15-second / LOW 45-second cooldown, mute, session budget and one in-flight request gate generation. Newer events invalidate pending output. USER_MESSAGE always silences and starts cooldown; it never generates an extra ordinary-chat reply. Closing/replacing/expiring a session discards late outputs. Ordinary-chat activity within 30 seconds suppresses real decisions even without client notification; its persisted message watermark is checked before slow preparation, before provider dispatch and after generation.

The real route only accepts the owner's existing `LanguageRouter` BYOK route; platform routes are rejected without invoking a provider. There is no fallback, model tool loop, repair call or retry. `LanguageModelInvoker.streamBounded` defaults to unsupported, so old adapters cannot silently ignore the generation cap. The existing OpenAI-compatible implementation adds `max_tokens:160` only to bounded calls; ordinary streaming body is unchanged. A single decision has a total eight-second wait, 1,024 UTF-16 character transport cap and maximum 100-code-point speech. Strict JSON rejects duplicate/unknown fields, trailing tokens, invalid enum combinations, control characters, angle brackets and URL-like content. Any invalid/unavailable result becomes SILENT.

Independent `companion_presence_usage` rows reserve **24 attempts per owner per Asia/Shanghai calendar day**, atomically with a unique day key, row lock and conditional increment. The reservation commits in REQUIRES_NEW before routing/calling; failure, invalid output and cancellation do not refund it. New sessions/JVM restarts cannot reset this daily counter. The additive migration and both ShardingSphere single-table lists include this table. No database was deployed or migrated outside isolated tests.

These are attempt/output-token limits, **not an exact currency spending guarantee**: BYOK prices, tokenizer behavior, reasoning-token accounting and provider support vary. The provider must honor its token parameter; unsupported providers fail without retry. An already-dispatched request may incur cost after local cancellation. Real BYOK remains server-disabled pending explicit operator configuration and user opt-in; demo tests do not establish real-model quality or vendor compliance.

## Verification

Focused Maven suite passed: **63 tests, zero failures/errors/skips**. Production/test compilation passed (544/160 source files). Breakdown: controller 4, migration 9, service 17, decision decoder 21, durable quota integration 3, existing OpenAI-compatible client 7, bounded client 2. The service suite includes a real eight-second stalled-flux timeout using a fixture, not a real provider.

Verified: authenticated ownership and sanitized rejection of extra fields; off/mute/stale/dedupe/cooldown/session-budget gates; three demo actions with no external dependencies; quota-before-route ordering; bounded transport with unchanged ordinary streaming; platform rejection; failure/overlong/invalid output without retries; close/user-message superseding; persisted chat arriving during blocked routing; expired input before dispatch; recent-chat suppression; concurrent database reservations; independent chat allowance; reservation surviving caller rollback; additive migration rollback/reapply.

Commands use official Maven 3.9.15 and full Temurin Java 21.0.12.1+1 offline, `-Dspring.profiles.active=test`, and explicit JaCoCo 0.8.13 + Mockito 5.17.0 startup agents. Focused selection:

```text
PresenceDecisionTest,CompanionPresenceServiceTest,CompanionPresenceControllerTest,PresenceBoundedLanguageClientTest,PresenceQuotaIntegrationTest,CompanionSchemaMigrationTest,OpenAiCompatibleLanguageClientTest
```

Evidence outside the repository: sibling `tool-cache/presence-tests.log` and `tool-cache/presence-reports/` XML reports. Initial attempts stopped before JUnit because of an expired invocation proxy, vendor-JDK CA mismatch, and missing dynamic test dependencies. Recovery used the existing system Java trust for official Maven dependency resolution, followed by offline full-JDK compilation/testing. No TLS validation, trust store, credentials, or security settings were changed. Full aggregate verification is separate and is recorded by the parent task.

### Aggregate and HTTP results

- Full offline `clean verify`: **842 tests, 838 passed, zero failures/errors, four intentional skips**, packaging and original JaCoCo gates passed. Domain branch coverage: airuntime 449/506; companion 551/636. The four skips are the pre-existing opt-in live vision smoke and three opt-in Redis collaboration tests, not presence cases.
- The first aggregate run had one documentation contract failure: new migration absent from the production release checklist. Added its filename and updated total, then reran the entire clean verification successfully; no product logic was changed for that failure. First-run XML/logs remain in sibling `tool-cache/full-first-failure/`; final XML and coverage are in `tool-cache/full-success-reports/` and `tool-cache/full-success-jacoco.xml`.
- Added `li-picture-cloud-frontend/e2e/api/presence.api.js` to the existing API-only fixture workflow. Real HTTP authentication, owner isolation, opt-in/default-off, BYOK default denial, unknown-field rejection, DEMO decision, canonical string sequence, dedupe, budget, mute, user-message priority, close, no history pollution and ordinary SSE chat after stop all passed.
- Fresh isolated API run: **40/40 TAP checks** (31 business stages, four protocol checks, five parent containers). This includes all existing companion/gateway/recipe/story stories and four new presence stages. Final run `/tmp/lpc-api-e2e-uZOCnz`; first run `/tmp/lpc-api-e2e-rDu7rC` failed only the old persona demo-text expectation and stopped that dependent storyline. The persona worker updated the fixture; the fresh rerun passed all checks.
- The actual server returns the long sequence as a decimal string, consistent with global Jackson configuration. HTTP verification caught this integration contract; the frontend transport now validates the string rather than rejecting it for differing from a JavaScript number.
- Fixture command explicitly pins `app.companion.presence.real-enabled=false`. Inventory checks require the presence story rather than silently accepting a partial checkout; five policy checks passed after this update.
- All fixture children are recorded closed in final `run.json`: preparation/checks/Redis exit 0; owned Java backend exits 143 after the harness’s planned SIGTERM. Only fresh memory H2 and loopback Redis6380/backend18124 were used; no existing service was reused or modified.
- Redis7.2.11 was built only inside sibling tool-cache from the official source archive, SHA256 `2f9886eca68d30114ad6a01da65631f8007d802fd3e6c9fac711251e6390323d`, matching the repository’s earlier verified record. No system-wide package installation, service change, TLS bypass, production credential or real provider call occurred.

Reproduce API-only checks with Node22 and prepared tools:

```bash
export LPC_API_JAVA_HOME=/path/to/jdk-21
export LPC_API_MAVEN_HOME=/path/to/writable/maven-home-with-wrapper-cache
export LPC_API_MAVEN_REPO=/path/to/cached/maven/repository
export LPC_API_REDIS_SERVER=/path/to/redis-7.2.11/src/redis-server
cd li-picture-cloud-frontend
npm run test:api:e2e
```

These results establish fixture transport and safety boundaries. They do not establish real-provider compliance, real-model personality, actual image understanding, browser interaction, visual quality or deployment readiness.
