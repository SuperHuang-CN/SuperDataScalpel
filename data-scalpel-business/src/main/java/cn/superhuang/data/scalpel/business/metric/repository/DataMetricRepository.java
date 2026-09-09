package cn.superhuang.data.scalpel.business.metric.repository;
import cn.superhuang.data.scalpel.business.metric.domain.DataMetric;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DataMetricRepository extends SearchRepository<DataMetric,UUID> {
    boolean existsByCode(String code);
    Optional<DataMetric> findByCode(String code);
    boolean existsByDirectoryId(UUID directoryId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DataMetric m where m.id=:id")
    Optional<DataMetric> findByIdForUpdate(@Param("id") UUID id);
    @Query("select new cn.superhuang.data.scalpel.business.metric.repository.DataMetricRepository$DirectoryResourceCount(m.directoryId,count(m)) from DataMetric m where m.directoryId in :ids group by m.directoryId")
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("ids") Collection<UUID> ids);
    record DirectoryResourceCount(UUID directoryId,long resourceCount) {}
}
