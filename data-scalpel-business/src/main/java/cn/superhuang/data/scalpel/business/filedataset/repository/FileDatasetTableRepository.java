package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDatasetTableRepository extends SearchRepository<FileDatasetTable, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FileDatasetTable item where item.id = :id")
    Optional<FileDatasetTable> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FileDatasetTable item where item.id = :id and item.fileDatasetId = :fileDatasetId")
    Optional<FileDatasetTable> findLockedByIdAndFileDatasetId(
            @Param("id") UUID id,
            @Param("fileDatasetId") UUID fileDatasetId
    );

    long countByFileDatasetId(UUID fileDatasetId);

    long countByFileDatasetIdAndParseStatus(UUID fileDatasetId, FileDatasetParseStatus parseStatus);

    List<FileDatasetTable> findByFileDatasetIdOrderByCreatedAtAsc(UUID fileDatasetId);

    Optional<FileDatasetTable> findByIdAndFileDatasetId(UUID id, UUID fileDatasetId);

    boolean existsByFileDatasetIdAndParseStatus(UUID fileDatasetId, FileDatasetParseStatus parseStatus);

    void deleteByFileDatasetId(UUID fileDatasetId);
}
