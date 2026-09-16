package cn.superhuang.data.scalpel.business.ontology.repository;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectType;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessObjectTypeRepository extends SearchRepository<BusinessObjectType, UUID> {
    boolean existsByCode(String code);

    boolean existsByDirectoryId(UUID directoryId);

    List<BusinessObjectType> findAllByEnabledTrueOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select type from BusinessObjectType type where type.id = :id")
    Optional<BusinessObjectType> findByIdForUpdate(@Param("id") UUID id);

    @Query("select new cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeRepository$DirectoryResourceCount(type.directoryId, count(type)) from BusinessObjectType type where type.directoryId in :ids group by type.directoryId")
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("ids") Collection<UUID> ids);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}
