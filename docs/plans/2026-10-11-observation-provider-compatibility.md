# Bounded observation provider compatibility

Evidence: user trace reports DASHSCOPE qwen3.8-max, HTTP 200, reasoningPresent true, no answer frames and cancellation. The existing observation independently cancels after eight seconds; this is consistent with the trace, but does not prove that every cancellation has that cause.

Official sources checked on 2026-10-11:
- https://www.alibabacloud.com/help/en/model-studio/deep-thinking documents default thinking and enable_thinking=false for Qwen hybrid models.
- https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-chat-completions documents max_tokens excluding Qwen thinking and max_completion_tokens covering total generation. The documented actual total may differ by up to ten tokens; 160 is a request parameter, not an exact billing guarantee.

Only bounded background requests for DASHSCOPE qwen3.8-max and qwen3.8-max-0902 select non-thinking and max_completion_tokens=160. Human selection, endpoints, normal chat and other provider parameters stay unchanged. The observation collects answer text for one absolute 15-second deadline and at most 1024 characters; no automatic paid retries. Metadata records COLLECTION_TIMEOUT before transport cancellation and retains only a reasoning-presence boolean. Reservation requires more than 20 seconds on the owner's existing lease. The observe request alone has a 30-second browser timeout, covering optional bounded vision plus text and return margin.

The exact authorized image metadata declares WebP; a bounded 64-byte read of its original object identifies a JPEG prefix and a non-whitelisted storage suffix. A prefix does not prove the complete object is decodable. Capture now permits only PNG/JPEG magic followed by the existing PNG/JPEG ImageIO whitelist, byte/pixel/dimension limits, five-second execution limit and permission/version rechecks. No decoder dependency was added; SVG and actual WebP remain unsupported. No actual image pixels or private model reasoning were retained or sent to a model during investigation.

Verification uses synthetic local HTTP responses, a nine-second delayed synthetic answer, total-deadline cancellation, genuine cancellation, output bounds, H2 lease boundaries, JPEG/PNG raster fixtures and rejected SVG/WebP. Browser checks intercept all APIs and providers. Local deployment preserves branch, private configuration, unrelated changes, counters, database processes and data containers. Real provider acceptance remains a human action.
