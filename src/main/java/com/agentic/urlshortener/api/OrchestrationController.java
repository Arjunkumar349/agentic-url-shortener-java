package com.agentic.urlshortener.api;

import com.agentic.urlshortener.orchestration.AgenticWorkflowService;
import com.agentic.urlshortener.orchestration.WorkflowDecisionResponse;
import com.agentic.urlshortener.orchestration.WorkflowAuditEvent;
import com.agentic.urlshortener.orchestration.WorkflowGraphResponse;
import com.agentic.urlshortener.orchestration.WorkflowReplanRequest;
import com.agentic.urlshortener.orchestration.WorkflowFailureRequest;
import com.agentic.urlshortener.orchestration.WorkflowFallbackRequest;
import com.agentic.urlshortener.orchestration.WorkflowScenario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orchestration")
public class OrchestrationController {

    private final AgenticWorkflowService workflowService;

    public OrchestrationController(AgenticWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/workflow")
    public ResponseEntity<WorkflowGraphResponse> getWorkflow() {
        return ResponseEntity.ok(workflowService.getWorkflowGraph());
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<WorkflowScenario>> getScenarios() {
        return ResponseEntity.ok(workflowService.getScenarios());
    }

    @GetMapping("/audit")
    public ResponseEntity<List<WorkflowAuditEvent>> getAuditTrail() {
        return ResponseEntity.ok(workflowService.getAuditTrail());
    }

    @PostMapping("/stages/{stageId}/approve")
    public ResponseEntity<WorkflowDecisionResponse> approveStage(@PathVariable String stageId) {
        return ResponseEntity.ok(workflowService.approveStage(stageId));
    }

    @PostMapping("/stages/{stageId}/retry")
    public ResponseEntity<WorkflowDecisionResponse> retryStage(@PathVariable String stageId) {
        return ResponseEntity.ok(workflowService.retryStage(stageId));
    }

    @PostMapping("/stages/{stageId}/rollback")
    public ResponseEntity<WorkflowDecisionResponse> rollbackStage(@PathVariable String stageId) {
        return ResponseEntity.ok(workflowService.rollbackStage(stageId));
    }

    @PostMapping("/stages/{stageId}/complete")
    public ResponseEntity<WorkflowDecisionResponse> completeStage(@PathVariable String stageId) {
        return ResponseEntity.ok(workflowService.completeStage(stageId));
    }

    @PostMapping("/stages/{stageId}/start")
    public ResponseEntity<WorkflowDecisionResponse> startStage(@PathVariable String stageId) {
        return ResponseEntity.ok(workflowService.startStage(stageId));
    }

    @PostMapping("/stages/{stageId}/fail")
    public ResponseEntity<WorkflowDecisionResponse> failStage(
            @PathVariable String stageId,
            @jakarta.validation.Valid @RequestBody WorkflowFailureRequest request) {
        return ResponseEntity.ok(workflowService.failStage(stageId, request.reason()));
    }

    @PostMapping("/stages/{stageId}/replan")
    public ResponseEntity<WorkflowDecisionResponse> replanStage(
            @PathVariable String stageId,
            @jakarta.validation.Valid @RequestBody WorkflowReplanRequest request) {
        return ResponseEntity.ok(workflowService.replanStage(stageId, request.context()));
    }

    @PostMapping("/stages/{stageId}/fallback")
    public ResponseEntity<WorkflowDecisionResponse> fallbackStage(
            @PathVariable String stageId,
            @jakarta.validation.Valid @RequestBody WorkflowFallbackRequest request) {
        return ResponseEntity.ok(workflowService.fallbackStage(stageId, request.strategy()));
    }
}
