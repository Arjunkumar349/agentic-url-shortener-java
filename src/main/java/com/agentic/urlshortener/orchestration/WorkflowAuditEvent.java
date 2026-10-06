package com.agentic.urlshortener.orchestration;

import java.time.Instant;

public record WorkflowAuditEvent(
        Instant timestamp,
        String action,
        String stageId,
        String state,
        String actor,
        String detail
) {}
