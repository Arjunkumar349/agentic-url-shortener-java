package com.agentic.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;

public record WorkflowReplanRequest(@NotBlank String context) {}
