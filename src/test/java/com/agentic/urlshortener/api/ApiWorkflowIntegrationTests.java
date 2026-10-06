package com.agentic.urlshortener.api;

import com.agentic.urlshortener.orchestration.AgenticWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiWorkflowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgenticWorkflowService workflowService;

    @BeforeEach
    void resetWorkflow() {
        workflowService.initialize();
    }

    @Test
    void createsRedirectsAndReturnsLinkAnalyticsThroughHttpApi() throws Exception {
        String alias = "test" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
                {"url":"https://example.com/api-test","alias":"%s"}
                """.formatted(alias);

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(alias))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/api/v1/links/" + alias));

        mockMvc.perform(get("/api/v1/links/{shortCode}", alias)
                        .header("User-Agent", "MockMvc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/api-test"));

        mockMvc.perform(get("/api/v1/links/{shortCode}/stats", alias))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1))
                .andExpect(jsonPath("$.recentVisits[0].userAgent").value("MockMvc"));
    }

    @Test
    void rejectsInvalidUrlAndReportsMissingShortCode() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"ftp://example.com/archive"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/links/not_found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesGatedWorkflowAuditAndContextReplanning() throws Exception {
        mockMvc.perform(post("/api/v1/orchestration/stages/implementation/start"))
                .andExpect(status().isConflict());
        completeApprovalStage("security-review");
        completeApprovalStage("compliance-check");
        mockMvc.perform(post("/api/v1/orchestration/stages/implementation/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));
        mockMvc.perform(post("/api/v1/orchestration/stages/implementation/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/orchestration/stages/implementation/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/orchestration/stages/architecture/replan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"context":"Retain visit data for 30 days"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
        mockMvc.perform(get("/api/v1/orchestration/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("APPROVE"))
                .andExpect(jsonPath("$[9].action").value("REPLAN"));
    }

    private void completeApprovalStage(String stageId) throws Exception {
        mockMvc.perform(post("/api/v1/orchestration/stages/{stageId}/approve", stageId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orchestration/stages/{stageId}/start", stageId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orchestration/stages/{stageId}/complete", stageId))
                .andExpect(status().isOk());
    }
}
