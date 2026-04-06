package com.copilot.observability;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages distributed trace context for request correlation.
 * Integrates with SLF4J MDC for structured logging with trace IDs.
 */
@Slf4j
@Data
public class TraceContext {

    private final String traceId;
    private final String sessionId;
    private final Instant startTime;

    private TraceContext(String traceId, String sessionId) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.startTime = Instant.now();
    }

    public static TraceContext create(String sessionId) {
        String traceId = "cop-" + UUID.randomUUID().toString().substring(0, 12);
        TraceContext ctx = new TraceContext(traceId, sessionId);
        ctx.activate();
        return ctx;
    }

    /**
     * Push trace ID into MDC for structured logging.
     */
    public void activate() {
        MDC.put("traceId", traceId);
        MDC.put("sessionId", sessionId != null ? sessionId : "anonymous");
        log.debug("Trace activated: {}", traceId);
    }

    /**
     * Clear MDC on request completion.
     */
    public void deactivate() {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        log.info("Trace {} completed in {}ms", traceId, durationMs);
        MDC.remove("traceId");
        MDC.remove("sessionId");
    }

    public long getElapsedMs() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
}
