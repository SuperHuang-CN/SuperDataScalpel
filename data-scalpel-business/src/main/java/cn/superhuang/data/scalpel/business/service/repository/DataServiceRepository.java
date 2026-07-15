package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataServiceRepository extends SearchRepository<DataService, UUID> {

    boolean existsByCode(String code);

    boolean existsByEngineId(UUID engineId);

    boolean existsByModelId(UUID modelId);

    boolean existsByEngineIdAndRoutePath(UUID engineId, String routePath);

    boolean existsByEngineIdAndRoutePathAndIdNot(UUID engineId, String routePath, UUID id);

    boolean existsByModelIdInAndStatus(Collection<UUID> modelIds, DataServiceStatus status);

    boolean existsByEngineIdAndModelIdInAndStatus(UUID engineId, Collection<UUID> modelIds, DataServiceStatus status);

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
