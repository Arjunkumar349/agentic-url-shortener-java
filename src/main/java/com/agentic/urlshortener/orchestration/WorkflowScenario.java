package com.agentic.urlshortener.orchestration;

import java.util.List;

public record WorkflowScenario(
        String name,
        String type,
        String objective,
        List<String> decomposition,
        List<String> orchestration,
        List<String> validation
) {}
