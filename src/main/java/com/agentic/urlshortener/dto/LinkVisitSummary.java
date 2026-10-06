package com.agentic.urlshortener.dto;

import java.time.Instant;

public record LinkVisitSummary(
        Instant visitedAt,
        String referrer,
        String userAgent,
        String ipAddress
) {}
