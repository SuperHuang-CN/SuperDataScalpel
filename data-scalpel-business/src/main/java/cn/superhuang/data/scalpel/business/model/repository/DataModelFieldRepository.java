package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataModelFieldRepository extends SearchRepository<DataModelField, UUID> {

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

    void deleteAllByModelId(UUID modelId);

    List<DataModelField> findAllByStandardDictionaryId(UUID standardDictionaryId);

    long countByStandardDictionaryId(UUID standardDictionaryId);

    boolean existsByStandardDictionaryId(UUID standardDictionaryId);
}
