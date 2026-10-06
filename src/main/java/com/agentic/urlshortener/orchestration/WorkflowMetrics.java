package com.agentic.urlshortener.orchestration;

public record WorkflowMetrics(
        double successRate,
        long retryCount,
        long rollbackCount,
        double meanTimeToRecoverMinutes,
        long endToEndLatencyMs
) {}
