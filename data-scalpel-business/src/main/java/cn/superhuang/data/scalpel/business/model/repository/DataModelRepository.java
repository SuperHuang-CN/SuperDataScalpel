package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataModelRepository extends SearchRepository<DataModel, UUID> {

    boolean existsByCode(String code);

    boolean existsByDirectoryId(UUID directoryId);

    boolean existsByStorageDataSourceId(UUID storageDataSourceId);

    boolean existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableName(
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName
    );

    boolean existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableNameAndIdNot(
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            UUID id
    );

    @Query("""
            select new cn.superhuang.data.scalpel.business.model.repository.DataModelRepository$DirectoryResourceCount(
                    model.directoryId, count(model))
            from DataModel model
            where model.directoryId in :directoryIds
            group by model.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}
