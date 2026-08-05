package cn.superhuang.data.scalpel.business.standard.repository;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandardDictionaryItemRepository extends JpaRepository<StandardDictionaryItem, UUID> {

    List<StandardDictionaryItem> findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(UUID dictionaryId);

    Optional<StandardDictionaryItem> findByDictionaryIdAndCode(UUID dictionaryId, String code);

    boolean existsByDictionaryIdAndCode(UUID dictionaryId, String code);

    boolean existsByDictionaryIdAndCodeAndIdNot(UUID dictionaryId, String code, UUID id);

    boolean existsByParentId(UUID parentId);

    long countByDictionaryId(UUID dictionaryId);

    void deleteAllByDictionaryId(UUID dictionaryId);
}
