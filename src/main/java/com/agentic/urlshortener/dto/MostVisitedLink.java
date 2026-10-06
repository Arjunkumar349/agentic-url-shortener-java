package com.agentic.urlshortener.dto;

public record MostVisitedLink(
        String shortCode,
        String originalUrl,
        Long clickCount
) {}
