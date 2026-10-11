# 灵叶 companion: runtime research and implementation guidance

Research date and source access date: **2026-10-09 (UTC)**.

This document separates verified public facts from proposed application design. It contains no private conversation excerpts or personal profiles. It does not claim that the recommendations below have all been implemented or evaluated.

## Recommendation and repository baseline

Keep the Java/Spring AI stack and the ordinary chat experience. Add the optional companion as a bounded event → policy gate → decision → presentation pipeline. A Python service, Agents SDK migration, autonomous tool loop, or model training is unnecessary for the initial version.

The checked-out `pom.xml` declares Java 21, Spring Boot 3.5.14, Spring AI BOM 1.1.2, and Spring AI Alibaba BOM 1.1.2.2. It already includes DashScope, Ollama, and pgvector-related dependencies. Reuse established adapters and storage where appropriate; do not introduce a second vector database just for the companion. Dependency presence alone does not prove a service is configured or operational.

**Version boundary:** the live Spring AI reference currently identifies itself as 2.0.1. It is useful for concepts but is not an API compatibility guarantee for this repository. In particular, do not copy new 2.0 structured-output switches, changed starter names, or current provider configuration blindly into Spring AI 1.1.2. Compile against the pinned dependencies and use their actual supported surface.

## 1. Event-driven behavior without an uncontrolled agent loop

The following is a proposed application architecture, not a description of Neuro's internals:

1. Preserve normal chat routes and direct user messages.
2. Make proactive companion behavior opt-in, with an obvious disable control.
3. Accept only allowlisted application events, such as explicit pet interaction, application navigation, or an acknowledged task result.
4. Derive ownership from authentication. Never trust an owner/user ID supplied in an event body.
5. Reject stale and duplicate events. Apply quiet hours, cooldowns, per-user limits, and concurrency limits before model invocation.
6. For an eligible event, make at most one short decision call without tool access.
7. Validate the result and recheck policy before presentation. Timeout, provider failure, malformed output, or missing context should produce silence.
8. Present pet speech/emotion separately from ordinary conversation history unless the user intentionally engages it.

A small endpoint and the application's existing delivery mechanism are enough initially. Do not add a broker or background polling model calls without evidence of need. Reserve a decision budget atomically before invocation; multi-tab requests and concurrent events must not bypass limits. Client-side hiding does not substitute for backend enforcement.

## 2. Cheap, bounded JSON decisions

Spring AI's converters are explicitly best-effort: object conversion alone does not guarantee that model output follows the requested contract. Provider-native structured output may improve shape reliability, but application validation remains necessary. [Spring AI output converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html)

Suggested contract:

- `action`: a fixed enum, for example `SILENT`, `SPEAK`, or `EMOTE`
- `text`: short, length-bounded plain text
- `emotion` or `animation`: a fixed allowlist
- Optional bounded duration; no arbitrary URL, HTML, command, tool, or executable payload

Use strict JSON parsing and explicit cross-field validation: silence has no speech, speak requires valid nonempty text, and every animation must exist. A valid JSON object is not sufficient evidence that its content is permitted. Reject unsupported fields/actions rather than trying to execute them. Avoid a second model call to repair malformed proactive output.

Set an explicit timeout, output-token budget, input-context budget, retry ceiling, and decision-call quota. Account for unsuccessful attempts too. Current provider documentation demonstrates retry behavior that can be much too generous for a small foreground companion; verify the actual pinned client defaults and override deliberately. [Spring AI OpenAI configuration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)

Maintain a small capability profile for each configured model/provider: schema output, JSON mode, streaming, temperature support, and the appropriate output-limit parameter. An OpenAI-compatible endpoint is not proof that every option behaves identically. Use the repository's configured provider; do not silently switch to a new paid model. Test adapters with fake responses before any authorized live calls.

## 3. Memory, provenance, retrieval, and forgetting

Spring AI distinguishes bounded conversational memory from full conversation history. Its documentation recommends separate persistence for complete history and notes limitations around intermediate tool messages. [Spring AI chat memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)

Recommended data separation:

- **History:** the existing authoritative record of messages
- **Working context:** a bounded recent window for the current exchange
- **Companion memory:** small, attributable facts and explicit preferences
- **Persona:** versioned application instructions and examples, separate from user facts

Useful memory fields include owner ID, fact, source message/event ID, observation time, expiry time, explicit-versus-inferred status, confidence, and superseded/deleted status. These are proposed domain fields, not built-in Spring AI guarantees.

Do not convert the companion's own generated claims into facts about the user. Prefer explicit statements and narrowly scoped application facts. Retrieved memories are contextual data, never higher-priority instructions. Corrections should supersede old records, and temporary plans should expire.

Start with a small owner-scoped recent/relevant selection using existing persistence. Introduce semantic retrieval only where it improves measured recall; reuse existing pgvector infrastructure if configured. Spring AI supports metadata filtering and deletion by ID/filter, but application authorization and scope remain the application's responsibility. [Spring AI vector stores](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)

Forgetting must cover derived memories, indexes, summaries, and caches. Prevent an old source message from immediately recreating a forgotten fact through later extraction. Define whether a request forgets a fact or also deletes source history; do not silently equate the two. Verify that forgotten content no longer enters prompts. Backup retention and any already-transmitted provider data need separate, honest treatment.

## 4. Persona and privacy boundaries

A compact, stable persona should specify a few traits, vocabulary preferences, examples, and limits. Liveliness should come from relevant reactions, expressive presentation, and varied but bounded behavior. Silence is a valid outcome.

Recommended behavior limits:

- No invented awareness of unseen activity or unsupported memories
- No guilt about user absence, exclusivity, demands for attention, or escalating interruptions
- No hidden ambient monitoring
- No changes to identity, safety rules, or tool permissions driven by retrieved content
- User correction and disable controls take priority over character consistency

Prefer compact application-event summaries. Avoid sending raw DOM text, clipboard data, screenshots, or unrestricted page content by default. If optional vision is added, define its explicit trigger, supported input, retention, provider destination, and disable behavior. Image access is not a reason to capture continuously.

Keep prompt, completion, memory, and tool-payload logging off by default. Log operational categories, timings, counts, and safe correlation IDs instead. Spring AI's observability documentation defaults potentially sensitive content logging off. [Spring AI observability](https://docs.spring.io/spring-ai/reference/observability/index.html)

## 5. OpenAI Agents SDK: transferable patterns, optional dependency

The SDK provides managed loops, tools, handoffs, sessions, guardrails, and tracing. Its own overview distinguishes owning a short workflow directly from adopting a managed runtime. Java can implement the needed bounded orchestration without a Python sidecar. [OpenAI Agents SDK overview](https://openai.github.io/openai-agents-python/)

Borrow explicit run limits, typed results, boundary validation, session ownership, and traceable outcomes. Do not add multiple agents merely to emulate personality.

If the SDK is adopted later, configure privacy before the first run: its Python tracing is enabled by default and sensitive generation/tool data inclusion defaults to true. Adding a redaction processor does not automatically replace the default exporter. [OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/)

## 6. Evaluation and release gates

Deterministic tests should establish:

- Feature disabled, quiet time, cooldown, quota exhausted, stale event, and duplicate event cause zero model calls
- Cross-user event/memory access is rejected
- Concurrent or multi-tab events cannot bypass budget reservation
- Invalid JSON, unsupported action, overlong text, timeout, and provider errors yield silence
- Event text and retrieved-memory prompt injection cannot change permissions
- Forgotten or superseded facts do not re-enter context
- Ordinary chat remains usable and unchanged
- Animations and durations stay within supported bounds
- UI disable/unmount/navigation clean up timers and in-flight behavior

For later authorized model evaluation, use a small human-reviewed scenario set covering relevance, naturalness, persona consistency, factual memory, non-repetition, and appropriate interruption. Include examples where silence is the correct answer. Track call counts and latency separately from subjective charm.

Spring AI's relevance and factual evaluators can supplement this process; they do not establish personality quality or safety. Model-based grading can incur additional calls and should not run silently in normal CI. [Spring AI evaluation testing](https://docs.spring.io/spring-ai/reference/api/testing.html)

## 7. Neuro: evidence versus speculation

Vedal's primary advice page identifies an LLM as Neuro-sama's main technology, declines to provide an exact tutorial, and encourages using familiar tools. The page explicitly says it was written in **2023** and may be outdated. Its historical signup-credit advice should not be treated as a current commercial offer. [Vedal's advice](https://vedal.ai/advice/)

This evidence does not establish Neuro's current underlying model, training corpus, memory layout, scheduler, retrieval implementation, or fine-tuning recipe. Those details should not be represented as verified facts. Community diagrams and clips may inspire experiments, but this document does not use them as architectural authority.

The defensible goal is an experience inspired by a lively AI character, implemented with transparent application-specific controls. It is not a reproduction of a publicly documented Neuro architecture.

## Scope and verification limits

This research used public primary documentation and a read of the repository's dependency declarations. No paid model calls, training, external deployments, or runtime benchmark claims are part of this research. Implementation and acceptance-test results belong in their respective change reports. All source links above were accessed on 2026-10-09; live documentation can change independently of the pinned dependencies.
