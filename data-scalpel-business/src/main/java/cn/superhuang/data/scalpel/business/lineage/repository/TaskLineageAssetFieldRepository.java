package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageAssetField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageAssetFieldRepository extends JpaRepository<TaskLineageAssetField, UUID> {
    List<TaskLineageAssetField> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);

    List<TaskLineageAssetField> findAllByAssetIdIn(Collection<UUID> assetIds);

    @Query("""
            select field
            from TaskLineageAssetField field
            where field.modelFieldId in :modelFieldIds
              and field.snapshotId in (
                    select snapshot.id
                    from TaskLineageSnapshot snapshot
                    where snapshot.retiredAt is null
              )
            """)
    List<TaskLineageAssetField> findCurrentByModelFieldIdIn(
            @Param("modelFieldIds") Collection<UUID> modelFieldIds
    );
}
