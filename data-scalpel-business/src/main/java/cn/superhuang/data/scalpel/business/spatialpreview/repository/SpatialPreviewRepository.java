package cn.superhuang.data.scalpel.business.spatialpreview.repository;

import cn.superhuang.data.scalpel.business.spatialpreview.domain.SpatialPreviewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpatialPreviewRepository extends JpaRepository<SpatialPreviewState, UUID> {
    Optional<SpatialPreviewState> findBySourceKey(String sourceKey);

    @Query("select p.generation from SpatialPreviewState p where "
            + "(p.state in ('READY','OVERVIEW_READY') and p.expiresAt > :now) "
            + "or (p.state = 'PREPARING' and p.leaseUntil > :now)")
    List<UUID> findRetainedGenerations(Instant now);
}
