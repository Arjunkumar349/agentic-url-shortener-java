package com.agentic.urlshortener.orchestration;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgenticWorkflowService {
    private static final int MAX_RETRIES_PER_STAGE = 3;
    private static final List<WorkflowStage> STAGES = List.of(WorkflowStage.values());

    private final Map<String, String> executionState = new LinkedHashMap<>();
    private final Map<String, Integer> stageRetries = new HashMap<>();
    private final Map<String, Instant> rollbackStartedAt = new HashMap<>();
    private final List<WorkflowAuditEvent> auditTrail = new ArrayList<>();
    private long retryCount;
    private long rollbackCount;
    private long recoveryDurationMs;
    private long recoveryCount;
    private long startedAtNanos;
    private int contextVersion;
    private String workflowContext = "Initial URL shortener delivery scope";

    @PostConstruct
    public synchronized void initialize() {
        executionState.clear();
        stageRetries.clear();
        rollbackStartedAt.clear();
        auditTrail.clear();
        retryCount = 0;
        rollbackCount = 0;
        recoveryDurationMs = 0;
        recoveryCount = 0;
        contextVersion = 1;
        workflowContext = "Initial URL shortener delivery scope";
        startedAtNanos = System.nanoTime();

        STAGES.forEach(stage -> executionState.put(stage.getId(), "PENDING"));
        executionState.put(WorkflowStage.REQUIREMENTS.getId(), "COMPLETED");
        executionState.put(WorkflowStage.ARCHITECTURE.getId(), "COMPLETED");
    }

    public synchronized WorkflowGraphResponse getWorkflowGraph() {
        List<WorkflowNodeResponse> nodes = STAGES.stream()
                .map(stage -> new WorkflowNodeResponse(
                        stage.getId(),
                        stage.name(),
                        stage.getDependencies(),
                        executionState.get(stage.getId()),
                        stage.isApprovalRequired(),
                        canProceed(stage)))
                .toList();

        long completed = STAGES.stream()
                .map(stage -> executionState.get(stage.getId()))
                .filter(state -> "COMPLETED".equals(state) || "APPROVED".equals(state))
                .count();
        WorkflowMetrics metrics = new WorkflowMetrics(
                completed * 100.0 / STAGES.size(),
                retryCount,
                rollbackCount,
                recoveryCount == 0 ? 0.0 : recoveryDurationMs / 60_000.0 / recoveryCount,
                Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis()
        );

        return new WorkflowGraphResponse(
                "url-shortener-agentic-workflow",
                "Governed SDLC workflow; context version " + contextVersion,
                nodes,
                getOverallState(),
                metrics
        );
    }

    public List<WorkflowScenario> getScenarios() {
        return List.of(
                new WorkflowScenario(
                        "Greenfield rollout",
                        "greenfield",
                        "Build the URL shortener from scratch with a clean architecture and full validation.",
                        List.of(
                                "Normalize requirements and agree URL, alias, and expiry policies.",
                                "Design link/visit entities, API contracts, and redirect flow.",
                                "Implement URL creation, redirect, analytics, and persistence.",
                                "Add unit and API-level tests, setup documentation, and release checklist."
                        ),
                        List.of(
                                "Requirements -> Architecture -> Security Review || Compliance Check -> Implementation",
                                "Implementation -> Testing || Documentation -> Release Readiness -> Exit",
                                "Human approval is required at security, compliance, implementation, and release gates."
                        ),
                        List.of(
                                "Create a link and verify the redirect Location header.",
                                "Verify click count and visit metadata after a redirect.",
                                "Require tests, documentation, and release approval before exit."
                        )
                ),
                new WorkflowScenario(
                        "Brownfield enhancement",
                        "brownfield",
                        "Add analytics or reliability behavior to an existing link service without breaking its redirect contract.",
                        List.of(
                                "Inventory controller, service, repository, schema, and existing API behavior.",
                                "Identify compatible extension points and add contract/regression tests first.",
                                "Implement the enhancement behind existing endpoint contracts.",
                                "Review migration, monitoring, rollback, and release impacts."
                        ),
                        List.of(
                                "Architecture -> Security Review || Compliance Check -> Implementation -> Testing",
                                "If an upstream design changes, re-plan and invalidate dependent stages.",
                                "Rollback blocks downstream release work until the affected stage is recovered."
                        ),
                        List.of(
                                "Compare existing redirect status and Location behavior before and after.",
                                "Run regression tests plus analytics correctness checks.",
                                "Verify rollback and downstream re-entry before release approval."
                        )
                ),
                new WorkflowScenario(
                        "Ambiguous requirement",
                        "ambiguous",
                        "Resolve alias, expiration, rate-limit, and data-retention policy questions before implementation.",
                        List.of(
                                "Record unresolved decisions and their security/compliance impact.",
                                "Offer explicit options and capture an accountable human decision.",
                                "Version the clarified context and re-plan impacted descendants.",
                                "Safe-stop implementation while required decisions or approvals are absent."
                        ),
                        List.of(
                                "Requirements gate -> Architecture -> Security Review || Compliance Check",
                                "Context change increments the decision version and resets affected downstream stages.",
                                "Retries are capped at three per stage; recovery requires the normal gates."
                        ),
                        List.of(
                                "Inspect the audit trail for the decision, actor label, and context version.",
                                "Verify implementation stays gated until all dependencies are approved.",
                                "Validate the newly planned work before release readiness."
                        )
                )
        );
    }

    public synchronized WorkflowDecisionResponse approveStage(String stageId) {
        WorkflowStage stage = requireStage(stageId);
        if (!stage.isApprovalRequired()) {
            throw conflict("Stage does not require human approval: " + stageId);
        }
        requireGateOpen(stage);
        String current = executionState.get(stageId);
        if (!List.of("PENDING", "IN_PROGRESS", "RETRYING", "ROLLED_BACK").contains(current)) {
            throw conflict("Stage cannot be approved from state " + current);
        }
        executionState.put(stageId, "APPROVED");
        return record("APPROVE", stageId, "APPROVED", "Human approval recorded for context version " + contextVersion);
    }

    public synchronized WorkflowDecisionResponse completeStage(String stageId) {
        WorkflowStage stage = requireStage(stageId);
        requireGateOpen(stage);
        String current = executionState.get(stageId);
        if (!"IN_PROGRESS".equals(current)) {
            throw conflict("Stage cannot be completed from state " + current);
        }

        executionState.put(stageId, "COMPLETED");
        Instant rollbackTime = rollbackStartedAt.remove(stageId);
        if (rollbackTime != null) {
            recoveryDurationMs += Duration.between(rollbackTime, Instant.now()).toMillis();
            recoveryCount++;
        }
        refreshBlockedStages();
        return record("COMPLETE", stageId, "COMPLETED", "Stage completed");
    }

    public synchronized WorkflowDecisionResponse startStage(String stageId) {
        WorkflowStage stage = requireStage(stageId);
        requireGateOpen(stage);
        String current = executionState.get(stageId);
        if (stage.isApprovalRequired() && !"APPROVED".equals(current)) {
            throw conflict("Human approval is required before starting stage: " + stageId);
        }
        if (!stage.isApprovalRequired() && !"PENDING".equals(current)) {
            throw conflict("Stage cannot be started from state " + current);
        }
        executionState.put(stageId, "IN_PROGRESS");
        return record("START", stageId, "IN_PROGRESS", "Stage execution started");
    }

    public synchronized WorkflowDecisionResponse failStage(String stageId, String reason) {
        requireStage(stageId);
        if (!"IN_PROGRESS".equals(executionState.get(stageId))) {
            throw conflict("Only an in-progress stage can be marked failed");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failure reason is required");
        }
        executionState.put(stageId, "FAILED");
        return record("FAIL", stageId, "FAILED", reason.trim());
    }

    public synchronized WorkflowDecisionResponse retryStage(String stageId) {
        WorkflowStage stage = requireStage(stageId);
        requireGateOpen(stage);
        String current = executionState.get(stageId);
        if (!List.of("FAILED", "ROLLED_BACK").contains(current)) {
            throw conflict("Stage cannot be retried from state " + current);
        }
        int attempts = stageRetries.getOrDefault(stageId, 0);
        if (attempts >= MAX_RETRIES_PER_STAGE) {
            throw conflict("Retry limit reached for stage: " + stageId);
        }
        stageRetries.put(stageId, attempts + 1);
        retryCount++;
        executionState.put(stageId, "PENDING");
        return record("RETRY", stageId, "PENDING",
                "Retry " + (attempts + 1) + " of " + MAX_RETRIES_PER_STAGE + " queued");
    }

    public synchronized WorkflowDecisionResponse rollbackStage(String stageId) {
        requireStage(stageId);
        String current = executionState.get(stageId);
        if (!List.of("IN_PROGRESS", "RETRYING", "APPROVED", "COMPLETED").contains(current)) {
            throw conflict("Stage cannot be rolled back from state " + current);
        }
        executionState.put(stageId, "ROLLED_BACK");
        rollbackStartedAt.put(stageId, Instant.now());
        rollbackCount++;
        for (String descendant : descendantsOf(stageId)) {
            executionState.put(descendant, "BLOCKED");
        }
        return record("ROLLBACK", stageId, "ROLLED_BACK",
                "Downstream stages blocked until this stage is recovered");
    }

    public synchronized WorkflowDecisionResponse replanStage(String stageId, String updatedContext) {
        WorkflowStage stage = requireStage(stageId);
        if (updatedContext == null || updatedContext.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Updated context is required");
        }
        contextVersion++;
        workflowContext = updatedContext.trim();
        resetStageAndDescendants(stageId, false);
        return record("REPLAN", stage.getId(), "PENDING",
                "Context version " + contextVersion + ": " + workflowContext);
    }

    public synchronized WorkflowDecisionResponse fallbackStage(String stageId, String alternative) {
        WorkflowStage stage = requireStage(stageId);
        String current = executionState.get(stageId);
        if (!List.of("FAILED", "ROLLED_BACK").contains(current)) {
            throw conflict("Fallback is only available after failure or rollback");
        }
        if (alternative == null || alternative.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fallback strategy is required");
        }
        contextVersion++;
        workflowContext = alternative.trim();
        resetStageAndDescendants(stageId, true);
        return record("FALLBACK", stage.getId(), "PENDING",
                "Alternative strategy selected at context version " + contextVersion + ": " + workflowContext);
    }

    public synchronized List<WorkflowAuditEvent> getAuditTrail() {
        return List.copyOf(auditTrail);
    }

    private WorkflowDecisionResponse record(String action, String stageId, String state, String detail) {
        auditTrail.add(new WorkflowAuditEvent(Instant.now(), action, stageId, state, "operator", detail));
        return new WorkflowDecisionResponse(stageId, state, detail);
    }

    private WorkflowStage requireStage(String stageId) {
        return STAGES.stream()
                .filter(stage -> stage.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stage: " + stageId));
    }

    private void requireGateOpen(WorkflowStage stage) {
        if (!canProceed(stage)) {
            throw conflict("Stage dependencies are not satisfied: " + stage.getId());
        }
    }

    private boolean canProceed(WorkflowStage stage) {
        return stage.getDependencies().stream().allMatch(dependency -> {
            String state = executionState.get(dependency);
            return "COMPLETED".equals(state) || "APPROVED".equals(state);
        });
    }

    private List<String> descendantsOf(String stageId) {
        List<String> descendants = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            for (WorkflowStage candidate : STAGES) {
                if (!candidate.getId().equals(stageId)
                        && !descendants.contains(candidate.getId())
                        && candidate.getDependencies().stream()
                        .anyMatch(dependency -> dependency.equals(stageId) || descendants.contains(dependency))) {
                    descendants.add(candidate.getId());
                    changed = true;
                }
            }
        } while (changed);
        return descendants;
    }

    private void refreshBlockedStages() {
        for (WorkflowStage stage : STAGES) {
            if ("BLOCKED".equals(executionState.get(stage.getId())) && canProceed(stage)) {
                executionState.put(stage.getId(), "PENDING");
            }
        }
    }

    private void resetStageAndDescendants(String stageId, boolean preserveRecoveryTimer) {
        executionState.put(stageId, "PENDING");
        if (!preserveRecoveryTimer) {
            rollbackStartedAt.remove(stageId);
        }
        for (String descendant : descendantsOf(stageId)) {
            executionState.put(descendant, "PENDING");
            rollbackStartedAt.remove(descendant);
        }
    }

    private String getOverallState() {
        if (executionState.containsValue("ROLLED_BACK")
                || executionState.containsValue("BLOCKED")
                || executionState.containsValue("FAILED")) {
            return "SAFE_STOP";
        }
        boolean allDone = STAGES.stream()
                .allMatch(stage -> "COMPLETED".equals(executionState.get(stage.getId())));
        return allDone ? "READY_FOR_RELEASE" : "IN_PROGRESS";
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
