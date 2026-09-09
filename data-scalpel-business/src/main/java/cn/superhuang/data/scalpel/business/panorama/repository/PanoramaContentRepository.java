package cn.superhuang.data.scalpel.business.panorama.repository;

import cn.superhuang.data.scalpel.business.panorama.domain.PanoramaContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface PanoramaContentRepository extends JpaRepository<PanoramaContent, UUID> {
    Optional<PanoramaContent> findByClientRequestId(UUID clientRequestId);
    List<PanoramaContent> findTop20ByCleanupPendingTrueAndCleanupAfterBeforeOrderByCleanupAfterAsc(Instant time);
}
