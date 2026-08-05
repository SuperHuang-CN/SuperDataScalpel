package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModelFieldTemplateItemRepository extends JpaRepository<ModelFieldTemplateItem, UUID> {

    List<ModelFieldTemplateItem> findAllByTemplateIdOrderBySortOrderAscCodeAsc(UUID templateId);

    List<ModelFieldTemplateItem> findAllByTemplateIdInOrderByTemplateIdAscSortOrderAscCodeAsc(
            Collection<UUID> templateIds
    );

    List<ModelFieldTemplateItem> findAllByStandardDictionaryId(UUID standardDictionaryId);

    long countByStandardDictionaryId(UUID standardDictionaryId);

    boolean existsByStandardDictionaryId(UUID standardDictionaryId);

    void deleteAllByTemplateId(UUID templateId);
}
