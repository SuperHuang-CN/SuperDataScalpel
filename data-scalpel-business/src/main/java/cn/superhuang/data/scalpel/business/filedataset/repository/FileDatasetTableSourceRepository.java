package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDatasetTableSourceRepository extends SearchRepository<FileDatasetTableSource, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FileDatasetTableSource item where item.id = :id")
    Optional<FileDatasetTableSource> findLockedById(@Param("id") UUID id);

    Optional<FileDatasetTableSource> findByIdAndFileDatasetTableId(UUID id, UUID tableId);

    List<FileDatasetTableSource> findByFileDatasetTableIdOrderBySourceOrderAsc(UUID tableId);

    List<FileDatasetTableSource> findByFileDatasetTableIdOrderByCreatedAtAsc(UUID tableId);

    List<FileDatasetTableSource> findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSourceOrderAsc(
            Collection<UUID> tableIds
    );

    List<FileDatasetTableSource> findBySourceFileIdOrderByCreatedAtAsc(UUID sourceFileId);

    List<FileDatasetTableSource> findBySourceFileIdIn(Collection<UUID> sourceFileIds);

    long countByFileDatasetTableId(UUID tableId);

    boolean existsBySourceFileId(UUID sourceFileId);

    void deleteByFileDatasetTableId(UUID tableId);

    void deleteByFileDatasetTableIdIn(Collection<UUID> tableIds);

    void deleteBySourceFileId(UUID sourceFileId);
}
