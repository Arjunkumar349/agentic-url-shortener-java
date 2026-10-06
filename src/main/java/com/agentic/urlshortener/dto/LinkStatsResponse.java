package com.agentic.urlshortener.dto;

import java.time.Instant;
import java.util.List;

public record LinkStatsResponse(
        String shortCode,
        String originalUrl,
        Long clickCount,
        Instant lastAccessedAt,
        List<LinkVisitSummary> recentVisits
) {}
