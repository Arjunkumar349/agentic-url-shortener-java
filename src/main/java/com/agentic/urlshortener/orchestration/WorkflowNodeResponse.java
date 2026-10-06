package com.agentic.urlshortener.orchestration;

import java.util.List;

public record WorkflowNodeResponse(
        String id,
        String label,
        List<String> dependencies,
        String status,
        boolean approvalRequired,
        boolean gateOpen
) {}
