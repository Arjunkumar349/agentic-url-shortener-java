package com.agentic.urlshortener.orchestration;

import java.util.List;

public record WorkflowGraphResponse(
        String workflowId,
        String description,
        List<WorkflowNodeResponse> nodes,
        String state,
        WorkflowMetrics metrics
) {}
