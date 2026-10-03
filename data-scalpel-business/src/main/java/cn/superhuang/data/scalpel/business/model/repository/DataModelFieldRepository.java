package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.search.SearchRepository;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataModelFieldRepository extends SearchRepository<DataModelField, UUID> {

    boolean existsByModelId(UUID modelId);

    List<DataModelField> findAllByModelIdOrderBySortOrderAscCodeAsc(UUID modelId);

    @Query("""
            select field
            from DataModelField field
            where field.modelId in :modelIds
            order by field.modelId, field.sortOrder, field.code
            """)
    List<DataModelField> findAllByModelIdInOrderByModelAndSort(
            @Param("modelIds") Collection<UUID> modelIds
    );

    @Query("""
            select new cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository$ModelFieldCount(
                    field.modelId, count(field))
            from DataModelField field
            where field.modelId in :modelIds
            group by field.modelId
            """)
    List<ModelFieldCount> countByModelIdIn(@Param("modelIds") Collection<UUID> modelIds);

    @Query("""
            select distinct field.modelId
            from DataModelField field
            where field.modelId in :modelIds and field.fieldType = :fieldType
            """)
    List<UUID> findModelIdsByFieldType(
            @Param("modelIds") Collection<UUID> modelIds,
            @Param("fieldType") PlatformDataType fieldType
    );

    void deleteAllByModelId(UUID modelId);

    List<DataModelField> findAllByStandardDictionaryId(UUID standardDictionaryId);

    long countByStandardDictionaryId(UUID standardDictionaryId);

    boolean existsByStandardDictionaryId(UUID standardDictionaryId);

    record ModelFieldCount(UUID modelId, long fieldCount) {
    }
}
