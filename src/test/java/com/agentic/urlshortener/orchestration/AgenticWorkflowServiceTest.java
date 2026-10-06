package com.agentic.urlshortener.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AgenticWorkflowServiceTest {

    private AgenticWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new AgenticWorkflowService();
        service.initialize();
    }

    @Test
    void exposesStageDependenciesAndApprovalGates() {
        var workflow = service.getWorkflowGraph();

        assertThat(workflow.nodes()).hasSize(9);
        assertThat(workflow.metrics().successRate()).isCloseTo(200.0 / 9.0, within(0.01));
        assertThat(workflow.nodes())
                .anySatisfy(node -> {
                    assertThat(node.id()).isEqualTo("implementation");
                    assertThat(node.dependencies())
                            .contains("architecture", "security-review", "compliance-check");
                    assertThat(node.gateOpen()).isFalse();
                    assertThat(node.approvalRequired()).isTrue();
                });
        assertThat(workflow.nodes())
                .filteredOn(node -> node.id().equals("security-review") || node.id().equals("compliance-check"))
                .allSatisfy(node -> {
                    assertThat(node.status()).isEqualTo("PENDING");
                    assertThat(node.approvalRequired()).isTrue();
                    assertThat(node.gateOpen()).isTrue();
                });
    }

    @Test
    void gatesHighImpactStageAndTracksRetryRollbackAndApproval() {
        assertThatThrownBy(() -> service.startStage("implementation"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        completeApprovalStage("security-review");
        completeApprovalStage("compliance-check");
        assertThat(service.approveStage("implementation").state()).isEqualTo("APPROVED");
        assertThat(service.startStage("implementation").state()).isEqualTo("IN_PROGRESS");
        assertThat(service.failStage("implementation", "Unit tests failed").state()).isEqualTo("FAILED");
        assertThat(service.retryStage("implementation").state()).isEqualTo("PENDING");
        assertThat(service.approveStage("implementation").state()).isEqualTo("APPROVED");
        assertThat(service.startStage("implementation").state()).isEqualTo("IN_PROGRESS");
        assertThat(service.rollbackStage("implementation").state()).isEqualTo("ROLLED_BACK");

        var metrics = service.getWorkflowGraph().metrics();
        assertThat(metrics.retryCount()).isEqualTo(1L);
        assertThat(metrics.rollbackCount()).isEqualTo(1L);
        assertThat(service.getWorkflowGraph().state()).isEqualTo("SAFE_STOP");
        assertThat(service.getAuditTrail()).extracting(WorkflowAuditEvent::action)
                .contains("APPROVE", "START", "FAIL", "RETRY", "ROLLBACK");
    }

    @Test
    void rejectsActionsForUnknownStages() {
        assertThatThrownBy(() -> service.approveStage("unknown"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.retryStage("unknown"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.rollbackStage("unknown"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void retryLimitIsEnforcedAndDependentStagesAreInvalidatedOnReplan() {
        completeApprovalStage("security-review");
        completeApprovalStage("compliance-check");
        service.approveStage("implementation");
        service.startStage("implementation");
        for (int attempt = 0; attempt < 3; attempt++) {
            service.failStage("implementation", "validation failed");
            service.retryStage("implementation");
            service.approveStage("implementation");
            service.startStage("implementation");
        }
        service.failStage("implementation", "retry budget exhausted");
        assertThatThrownBy(() -> service.retryStage("implementation"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.fallbackStage("implementation", "Use a simpler persistence adapter").state())
                .isEqualTo("PENDING");
        service.approveStage("implementation");
        service.startStage("implementation");
        service.completeStage("implementation");
        assertThat(service.getWorkflowGraph().nodes())
                .anySatisfy(node -> {
                    assertThat(node.id()).isEqualTo("testing");
                    assertThat(node.status()).isEqualTo("PENDING");
                    assertThat(node.gateOpen()).isTrue();
                });

        var replan = service.replanStage("architecture", "Add retention requirements");
        assertThat(replan.state()).isEqualTo("PENDING");
        assertThat(service.getWorkflowGraph().state()).isEqualTo("IN_PROGRESS");
        assertThat(service.getAuditTrail()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo("REPLAN");
            assertThat(event.detail()).contains("Context version 3", "Add retention requirements");
        });
        assertThat(service.getAuditTrail()).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo("FALLBACK");
            assertThat(event.detail()).contains("Use a simpler persistence adapter");
        });
    }

    private void completeApprovalStage(String stageId) {
        service.approveStage(stageId);
        service.startStage(stageId);
        service.completeStage(stageId);
    }
}
