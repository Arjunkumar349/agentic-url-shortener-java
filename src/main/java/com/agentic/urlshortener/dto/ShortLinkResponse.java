package com.agentic.urlshortener.dto;

import java.time.Instant;

public record ShortLinkResponse(
        Long id,
        String shortCode,
        String originalUrl,
        String shortUrl,
        Instant createdAt,
        Long clickCount
) {}
