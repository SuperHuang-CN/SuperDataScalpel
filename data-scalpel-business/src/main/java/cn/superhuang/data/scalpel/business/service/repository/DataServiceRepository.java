package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataServiceRepository extends SearchRepository<DataService, UUID> {

    boolean existsByCode(String code);

    boolean existsByEngineId(UUID engineId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from DataService service where service.id = :id")
    java.util.Optional<DataService> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdInAndStatus(Collection<UUID> ids, DataServiceStatus status);

    boolean existsByEngineIdAndIdInAndStatus(UUID engineId, Collection<UUID> ids, DataServiceStatus status);

    List<DataService> findAllByIdIn(Collection<UUID> ids);

    @Query("""
            select new cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository$DirectoryResourceCount(
                    service.directoryId, count(service))
            from DataService service
            where service.directoryId in :directoryIds
            group by service.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}
