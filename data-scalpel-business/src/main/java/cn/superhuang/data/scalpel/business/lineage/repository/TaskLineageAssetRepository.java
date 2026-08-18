package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageAssetRepository extends JpaRepository<TaskLineageAsset, UUID> {

    List<TaskLineageAsset> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);

    @Query("""
            select asset
            from TaskLineageAsset asset
            where asset.modelId in :modelIds
              and asset.snapshotId in (
                    select snapshot.id
                    from TaskLineageSnapshot snapshot
                    where snapshot.retiredAt is null
              )
            """)
    List<TaskLineageAsset> findCurrentByModelIdIn(@Param("modelIds") Collection<UUID> modelIds);

    @Query("""
            select case when count(asset) > 0 then true else false end
            from TaskLineageAsset asset
            where asset.modelId = :modelId
              and asset.snapshotId in (
                    select snapshot.id
                    from TaskLineageSnapshot snapshot
                    where snapshot.retiredAt is null
              )
            """)
    boolean existsCurrentByModelId(@Param("modelId") UUID modelId);
}
