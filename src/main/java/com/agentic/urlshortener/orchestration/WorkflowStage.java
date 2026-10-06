package com.agentic.urlshortener.orchestration;

import java.util.List;

public enum WorkflowStage {
    REQUIREMENTS("requirements", "Capture and normalize scope", List.of(), true),
    ARCHITECTURE("architecture", "Design the system and data flow", List.of("requirements"), false),
    SECURITY_REVIEW("security-review", "Audit security controls and guardrails", List.of("architecture"), true),
    COMPLIANCE_CHECK("compliance-check", "Verify governance and change control", List.of("architecture"), true),
    IMPLEMENTATION("implementation", "Build the URL shortener service", List.of("architecture", "security-review", "compliance-check"), true),
    TESTING("testing", "Validate API and analytics behavior", List.of("implementation"), false),
    DOCUMENTATION("documentation", "Prepare setup, architecture, and scenario guidance", List.of("implementation", "testing"), false),
    RELEASE_READINESS("release-readiness", "Final signoff and operational review", List.of("documentation", "testing"), true),
    EXIT("exit", "Closed workflow with audit trail", List.of("release-readiness"), false);

    private final String id;
    private final String description;
    private final List<String> dependencies;
    private final boolean approvalRequired;

    WorkflowStage(String id, String description, List<String> dependencies, boolean approvalRequired) {
        this.id = id;
        this.description = description;
        this.dependencies = dependencies;
        this.approvalRequired = approvalRequired;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public List<String> getDependencies() { return dependencies; }
    public boolean isApprovalRequired() { return approvalRequired; }
}
