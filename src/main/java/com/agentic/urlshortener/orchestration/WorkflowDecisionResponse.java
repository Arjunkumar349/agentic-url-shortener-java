package com.agentic.urlshortener.orchestration;

public record WorkflowDecisionResponse(
        String stageId,
        String state,
        String message
) {}
