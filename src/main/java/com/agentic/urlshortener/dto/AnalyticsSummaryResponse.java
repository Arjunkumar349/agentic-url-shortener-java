package com.agentic.urlshortener.dto;

import java.util.List;

public record AnalyticsSummaryResponse(
        Long totalLinks,
        Long totalClicks,
        Long activeLinks,
        List<MostVisitedLink> popularLinks
) {}
