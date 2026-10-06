package com.agentic.urlshortener.service;

import com.agentic.urlshortener.domain.LinkEntity;
import com.agentic.urlshortener.domain.LinkVisit;
import com.agentic.urlshortener.dto.*;
import com.agentic.urlshortener.repository.LinkRepository;
import com.agentic.urlshortener.repository.LinkVisitRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UrlShortenerService {
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,20}$");

    private final LinkRepository linkRepository;
    private final LinkVisitRepository linkVisitRepository;

    public UrlShortenerService(LinkRepository linkRepository, LinkVisitRepository linkVisitRepository) {
        this.linkRepository = linkRepository;
        this.linkVisitRepository = linkVisitRepository;
    }

    @Transactional
    public ShortLinkResponse createShortUrl(CreateShortUrlRequest request) {
        String rawUrl = request.url() == null ? "" : request.url().trim();
        validateUrl(rawUrl);

        String candidateCode = request.alias() == null || request.alias().isBlank()
                ? generateCode()
                : sanitizeAlias(request.alias());

        if (linkRepository.existsByShortCode(candidateCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already exists");
        }

        LinkEntity link = new LinkEntity();
        link.setShortCode(candidateCode);
        link.setOriginalUrl(rawUrl);
        link.setStatus("ACTIVE");
        link.setClickCount(0L);
        linkRepository.save(link);

        return new ShortLinkResponse(
                link.getId(),
                link.getShortCode(),
                link.getOriginalUrl(),
                "http://localhost:8080/api/v1/links/" + link.getShortCode(),
                link.getCreatedAt(),
                link.getClickCount()
        );
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode, HttpServletRequest request) {
        LinkEntity link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));

        link.incrementClickCount();
        link.setLastAccessedAt(Instant.now());
        linkRepository.save(link);

        LinkVisit visit = new LinkVisit();
        visit.setLink(link);
        visit.setReferrer(request.getHeader("Referer"));
        visit.setUserAgent(request.getHeader("User-Agent"));
        visit.setIpAddress(request.getRemoteAddr());
        linkVisitRepository.save(visit);

        return link.getOriginalUrl();
    }

    public LinkStatsResponse getStats(String shortCode) {
        LinkEntity link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));

        List<LinkVisitSummary> visits = linkVisitRepository.findTop10ByLinkOrderByVisitedAtDesc(link)
                .stream()
                .map(visit -> new LinkVisitSummary(
                        visit.getVisitedAt(),
                        visit.getReferrer(),
                        visit.getUserAgent(),
                        visit.getIpAddress()))
                .toList();

        return new LinkStatsResponse(
                link.getShortCode(),
                link.getOriginalUrl(),
                link.getClickCount(),
                link.getLastAccessedAt(),
                visits
        );
    }

    public AnalyticsSummaryResponse getAnalyticsSummary() {
        List<LinkEntity> popularLinks = linkRepository.findTop10ByOrderByClickCountDesc();
        List<MostVisitedLink> rankedLinks = popularLinks.stream()
                .map(link -> new MostVisitedLink(link.getShortCode(), link.getOriginalUrl(), link.getClickCount()))
                .toList();

        long totalClicks = linkRepository.findAll().stream().mapToLong(LinkEntity::getClickCount).sum();
        long totalLinks = linkRepository.count();
        long activeLinks = linkRepository.findAll().stream().filter(link -> "ACTIVE".equals(link.getStatus())).count();

        return new AnalyticsSummaryResponse(totalLinks, totalClicks, activeLinks, rankedLinks);
    }

    private void validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }

        try {
            URI uri = URI.create(rawUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must include a valid scheme and host");
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http and https URLs are supported");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is invalid");
        }
    }

    private String sanitizeAlias(String alias) {
        String sanitized = alias.trim();
        if (!ALIAS_PATTERN.matcher(sanitized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alias must be 4-20 characters and contain only letters, numbers, underscores, or hyphens");
        }
        return sanitized;
    }

    private String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
