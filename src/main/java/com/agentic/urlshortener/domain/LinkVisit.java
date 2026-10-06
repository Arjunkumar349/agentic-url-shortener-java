package com.agentic.urlshortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "link_visits")
public class LinkVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private LinkEntity link;

    @Column(nullable = false)
    private Instant visitedAt;

    @Column(length = 512)
    private String referrer;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 128)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        this.visitedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LinkEntity getLink() { return link; }
    public void setLink(LinkEntity link) { this.link = link; }
    public Instant getVisitedAt() { return visitedAt; }
    public void setVisitedAt(Instant visitedAt) { this.visitedAt = visitedAt; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
