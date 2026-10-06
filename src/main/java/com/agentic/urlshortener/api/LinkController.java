package com.agentic.urlshortener.api;

import com.agentic.urlshortener.dto.AnalyticsSummaryResponse;
import com.agentic.urlshortener.dto.CreateShortUrlRequest;
import com.agentic.urlshortener.dto.LinkStatsResponse;
import com.agentic.urlshortener.dto.ShortLinkResponse;
import com.agentic.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class LinkController {

    private final UrlShortenerService urlShortenerService;

    public LinkController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/links")
    public ResponseEntity<ShortLinkResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        return ResponseEntity.ok(urlShortenerService.createShortUrl(request));
    }

    @GetMapping("/links/{shortCode}")
    public ResponseEntity<Void> redirectToTarget(@PathVariable String shortCode, jakarta.servlet.http.HttpServletRequest request) {
        String target = urlShortenerService.resolveOriginalUrl(shortCode, request);
        System.out.println(target);
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }

    @GetMapping("/links/{shortCode}/stats")
    public ResponseEntity<LinkStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlShortenerService.getStats(shortCode));
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary() {
        return ResponseEntity.ok(urlShortenerService.getAnalyticsSummary());
    }
}
