package cn.superhuang.data.scalpel.business.asset.repository;

import cn.superhuang.data.scalpel.business.asset.domain.Asset;
import cn.superhuang.data.scalpel.business.asset.domain.AssetStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends SearchRepository<Asset, UUID> {

    boolean existsByAssetTypeAndResourceId(AssetType assetType, UUID resourceId);

    Optional<Asset> findByAssetTypeAndResourceId(AssetType assetType, UUID resourceId);

    Optional<Asset> findByIdAndStatus(UUID id, AssetStatus status);

    List<Asset> findAllByAssetTypeAndResourceIdIn(AssetType assetType, Collection<UUID> resourceIds);

    boolean existsByDirectoryId(UUID directoryId);

    long countByStatus(AssetStatus status);

    @Query("select asset.tagsJson from Asset asset where asset.status = :status")
    List<String> findTagsJsonByStatus(@Param("status") AssetStatus status);

    @Query("""
            select new cn.superhuang.data.scalpel.business.asset.repository.AssetRepository$TypeResourceCount(
                    asset.assetType, count(asset))
            from Asset asset
            where asset.status = :status
            group by asset.assetType
            """)
    List<TypeResourceCount> countByStatusGroupByType(@Param("status") AssetStatus status);

    @Query("""
            select new cn.superhuang.data.scalpel.business.asset.repository.AssetRepository$DirectoryResourceCount(
                    asset.directoryId, count(asset))
            from Asset asset
            where asset.status = :status and asset.directoryId in :directoryIds
            group by asset.directoryId
            """)
    List<DirectoryResourceCount> countByStatusAndDirectoryIdIn(
            @Param("status") AssetStatus status,
            @Param("directoryIds") Collection<UUID> directoryIds
    );

    @Query("""
            select new cn.superhuang.data.scalpel.business.asset.repository.AssetRepository$DirectoryResourceCount(
                    asset.directoryId, count(asset))
            from Asset asset
            where asset.directoryId in :directoryIds
            group by asset.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }

    record TypeResourceCount(AssetType assetType, long resourceCount) {
    }
}
