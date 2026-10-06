package com.agentic.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;

public record WorkflowFailureRequest(@NotBlank String reason) {}
