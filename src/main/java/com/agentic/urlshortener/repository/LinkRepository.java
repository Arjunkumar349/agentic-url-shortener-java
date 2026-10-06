package com.agentic.urlshortener.repository;

import com.agentic.urlshortener.domain.LinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {
    Optional<LinkEntity> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);

    @Query("SELECT l FROM LinkEntity l ORDER BY l.clickCount DESC, l.createdAt DESC")
    List<LinkEntity> findTop10ByOrderByClickCountDesc();
}
