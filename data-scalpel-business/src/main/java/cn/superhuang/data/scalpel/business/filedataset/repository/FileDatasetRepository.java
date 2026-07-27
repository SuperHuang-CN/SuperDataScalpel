package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDatasetRepository extends SearchRepository<FileDataset, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dataset from FileDataset dataset where dataset.id = :id")
    Optional<FileDataset> findLockedById(@Param("id") UUID id);

    boolean existsByDirectoryId(UUID directoryId);

    @Query("""
            select new cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository$DirectoryResourceCount(
                    dataset.directoryId, count(dataset))
            from FileDataset dataset
            where dataset.directoryId in :directoryIds
            group by dataset.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}
