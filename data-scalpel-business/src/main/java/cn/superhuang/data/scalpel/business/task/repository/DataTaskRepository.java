package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface DataTaskRepository extends SearchRepository<DataTask, UUID> {

    boolean existsByCode(String code);

    boolean existsByDirectoryId(UUID directoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from DataTask task where task.id = :id")
    Optional<DataTask> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select new cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository$DirectoryResourceCount(
                    task.directoryId, count(task))
            from DataTask task
            where task.directoryId in :directoryIds
            group by task.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}
