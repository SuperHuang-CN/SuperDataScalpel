package cn.superhuang.data.scalpel.business.standard.repository;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandardDictionaryRepository extends SearchRepository<StandardDictionary, UUID> {

    Optional<StandardDictionary> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<StandardDictionary> findAllByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dictionary from StandardDictionary dictionary where dictionary.id = :id")
    Optional<StandardDictionary> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dictionary from StandardDictionary dictionary where dictionary.code in :codes")
    List<StandardDictionary> findAllByCodeInForUpdate(@Param("codes") Collection<String> codes);
}
