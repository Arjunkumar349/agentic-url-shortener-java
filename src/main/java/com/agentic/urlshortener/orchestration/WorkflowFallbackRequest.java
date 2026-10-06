package com.agentic.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;

public record WorkflowFallbackRequest(@NotBlank String strategy) {}
