package com.agentic.urlshortener.repository;

import com.agentic.urlshortener.domain.LinkEntity;
import com.agentic.urlshortener.domain.LinkVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkVisitRepository extends JpaRepository<LinkVisit, Long> {
    long countByLink(LinkEntity link);
    List<LinkVisit> findTop10ByLinkOrderByVisitedAtDesc(LinkEntity link);
}
