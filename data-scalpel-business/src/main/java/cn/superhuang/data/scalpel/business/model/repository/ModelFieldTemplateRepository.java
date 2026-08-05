package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplate;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModelFieldTemplateRepository extends SearchRepository<ModelFieldTemplate, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from ModelFieldTemplate template where template.id = :id")
    Optional<ModelFieldTemplate> findByIdForUpdate(@Param("id") UUID id);
}
