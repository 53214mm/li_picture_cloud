package com.li.lipicturecloud.application.airuntime;

import java.util.Set;

/** Request-local metadata only. Never accepts or retains reasoning, body, headers or routes. */
public final class LanguageStreamDiagnostics {
    private boolean supported;
    private Integer httpStatus, contentFrames;
    private Long transportTimeoutMillis;
    private Boolean reasoningPresent;
    private String finishReason, terminal;

    public synchronized void started(long timeoutMillis) {
        supported = true; transportTimeoutMillis = timeoutMillis; contentFrames = 0; reasoningPresent = false;
    }
    public synchronized void status(int status) { if (status >= 100 && status <= 599) httpStatus = status; }
    public synchronized void frame(boolean content, boolean reasoning, String finish, boolean done) {
        if (content) contentFrames = Math.min(4096, contentFrames == null ? 1 : contentFrames + 1);
        if (reasoning) reasoningPresent = true;
        if (finish != null && !finish.isBlank()) {
            finishReason = Set.of("stop", "length", "content_filter", "tool_calls", "function_call").contains(finish) ? finish : "OTHER";
            if (terminal == null) terminal = "FINISH_REASON";
        } else if (done && terminal == null) terminal = "DONE";
    }
    public synchronized void ended(String kind) {
        if (!Set.of("EOF", "ERROR", "TIMEOUT", "COLLECTION_TIMEOUT", "CANCELLED").contains(kind)) throw new IllegalArgumentException("Invalid terminal kind");
        if (terminal == null) terminal = kind;
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(supported, httpStatus, transportTimeoutMillis, contentFrames, reasoningPresent, finishReason, terminal);
    }
    public record Snapshot(boolean supported, Integer httpStatus, Long transportTimeoutMillis, Integer contentFrames,
                           Boolean reasoningPresent, String finishReason, String terminal) { }
}
