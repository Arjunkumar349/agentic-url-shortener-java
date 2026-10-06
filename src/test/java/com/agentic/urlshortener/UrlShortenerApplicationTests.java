package com.agentic.urlshortener;

import com.agentic.urlshortener.dto.CreateShortUrlRequest;
import com.agentic.urlshortener.dto.ShortLinkResponse;
import com.agentic.urlshortener.orchestration.AgenticWorkflowService;
import com.agentic.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerApplicationTests {

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private AgenticWorkflowService workflowService;

    @Test
    void createShortUrlAndTrackAnalytics() {
        var request = new CreateShortUrlRequest("https://example.com/docs", null);
        ShortLinkResponse response = urlShortenerService.createShortUrl(request);

        assertThat(response.shortCode()).isNotBlank();
        assertThat(response.originalUrl()).isEqualTo("https://example.com/docs");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("User-Agent", "JUnit Test");
        servletRequest.addHeader("Referer", "https://www.example.com");
        servletRequest.setRemoteAddr("127.0.0.1");

        String resolved = urlShortenerService.resolveOriginalUrl(response.shortCode(), servletRequest);
        assertThat(resolved).isEqualTo("https://example.com/docs");

        var stats = urlShortenerService.getStats(response.shortCode());
        assertThat(stats.clickCount()).isEqualTo(1L);
        assertThat(stats.recentVisits()).isNotEmpty();

        var summary = urlShortenerService.getAnalyticsSummary();
        assertThat(summary.totalLinks()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.totalClicks()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void orchestrationDefinesRequiredGatesAndScenarios() {
        var workflow = workflowService.getWorkflowGraph();
        assertThat(workflow.nodes()).isNotEmpty();
        assertThat(workflow.nodes()).anyMatch(node -> node.id().equals("implementation"));

        var scenarios = workflowService.getScenarios();
        assertThat(scenarios).hasSize(3);
    }
}
