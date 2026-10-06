package com.agentic.urlshortener.service;

import com.agentic.urlshortener.domain.LinkEntity;
import com.agentic.urlshortener.domain.LinkVisit;
import com.agentic.urlshortener.dto.CreateShortUrlRequest;
import com.agentic.urlshortener.repository.LinkRepository;
import com.agentic.urlshortener.repository.LinkVisitRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private LinkVisitRepository linkVisitRepository;

    @Mock
    private HttpServletRequest servletRequest;

    @InjectMocks
    private UrlShortenerService service;

    @Test
    void createsShortUrlWithRequestedAlias() {
        when(linkRepository.existsByShortCode("docs_2026")).thenReturn(false);
        when(linkRepository.save(any(LinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createShortUrl(new CreateShortUrlRequest("  https://example.com/docs  ", "docs_2026"));

        assertThat(response.shortCode()).isEqualTo("docs_2026");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/docs");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/api/v1/links/docs_2026");
        assertThat(response.clickCount()).isZero();

        ArgumentCaptor<LinkEntity> captor = ArgumentCaptor.forClass(LinkEntity.class);
        verify(linkRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void generatesEightCharacterCodeWhenAliasIsMissing() {
        when(linkRepository.existsByShortCode(anyString())).thenReturn(false);
        when(linkRepository.save(any(LinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createShortUrl(new CreateShortUrlRequest("https://example.com", null));

        assertThat(response.shortCode()).matches("[A-Fa-f0-9]{8}");
        verify(linkRepository).save(any(LinkEntity.class));
    }

    @Test
    void rejectsDuplicateAliasWithConflict() {
        when(linkRepository.existsByShortCode("docs_2026")).thenReturn(true);

        assertThatThrownBy(() -> service.createShortUrl(
                new CreateShortUrlRequest("https://example.com", "docs_2026")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(linkRepository, never()).save(any(LinkEntity.class));
    }

    @Test
    void rejectsUnsupportedUrlScheme() {
        assertThatThrownBy(() -> service.createShortUrl(new CreateShortUrlRequest("ftp://example.com/file", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(linkRepository, linkVisitRepository);
    }

    @Test
    void rejectsAliasOutsideAllowedFormat() {
        assertThatThrownBy(() -> service.createShortUrl(
                new CreateShortUrlRequest("https://example.com", "bad alias")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(linkRepository, never()).save(any(LinkEntity.class));
    }

    @Test
    void resolvesLinkAndRecordsVisitMetadata() {
        LinkEntity link = new LinkEntity();
        link.setShortCode("docs_2026");
        link.setOriginalUrl("https://example.com/docs");
        link.setClickCount(2L);
        when(linkRepository.findByShortCode("docs_2026")).thenReturn(Optional.of(link));
        when(servletRequest.getHeader("Referer")).thenReturn("https://search.example/");
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        String target = service.resolveOriginalUrl("docs_2026", servletRequest);

        assertThat(target).isEqualTo("https://example.com/docs");
        assertThat(link.getClickCount()).isEqualTo(3L);
        assertThat(link.getLastAccessedAt()).isNotNull();

        ArgumentCaptor<LinkVisit> visitCaptor = ArgumentCaptor.forClass(LinkVisit.class);
        verify(linkVisitRepository).save(visitCaptor.capture());
        assertThat(visitCaptor.getValue().getLink()).isSameAs(link);
        assertThat(visitCaptor.getValue().getReferrer()).isEqualTo("https://search.example/");
        assertThat(visitCaptor.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(visitCaptor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void missingLinkReturnsNotFoundForRedirectAndStats() {
        when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOriginalUrl("missing", servletRequest))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getStats("missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(linkVisitRepository, never()).save(any(LinkVisit.class));
    }

    @Test
    void returnsStatsAndAnalyticsFromRepositories() {
        LinkEntity link = new LinkEntity();
        link.setShortCode("docs_2026");
        link.setOriginalUrl("https://example.com/docs");
        link.setClickCount(4L);
        link.setStatus("ACTIVE");
        LinkVisit visit = new LinkVisit();
        visit.setVisitedAt(Instant.parse("2026-01-01T00:00:00Z"));
        visit.setReferrer("https://search.example/");
        visit.setUserAgent("JUnit");
        visit.setIpAddress("127.0.0.1");
        when(linkRepository.findByShortCode("docs_2026")).thenReturn(Optional.of(link));
        when(linkVisitRepository.findTop10ByLinkOrderByVisitedAtDesc(link)).thenReturn(List.of(visit));
        when(linkRepository.findTop10ByOrderByClickCountDesc()).thenReturn(List.of(link));
        when(linkRepository.findAll()).thenReturn(List.of(link));
        when(linkRepository.count()).thenReturn(1L);

        var stats = service.getStats("docs_2026");
        var summary = service.getAnalyticsSummary();

        assertThat(stats.clickCount()).isEqualTo(4L);
        assertThat(stats.recentVisits()).hasSize(1);
        assertThat(stats.recentVisits().get(0).userAgent()).isEqualTo("JUnit");
        assertThat(summary.totalLinks()).isEqualTo(1L);
        assertThat(summary.totalClicks()).isEqualTo(4L);
        assertThat(summary.activeLinks()).isEqualTo(1L);
        assertThat(summary.popularLinks()).hasSize(1);
    }
}
