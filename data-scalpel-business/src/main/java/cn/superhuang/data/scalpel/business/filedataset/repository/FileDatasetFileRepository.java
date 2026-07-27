package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDatasetFileRepository extends SearchRepository<FileDatasetFile, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FileDatasetFile item where item.id = :id")
    Optional<FileDatasetFile> findLockedById(@Param("id") UUID id);

    long countByFileDatasetId(UUID fileDatasetId);

    List<FileDatasetFile> findByFileDatasetIdOrderByCreatedAtAsc(UUID fileDatasetId);

    Optional<FileDatasetFile> findByIdAndFileDatasetId(UUID id, UUID fileDatasetId);

    void deleteByFileDatasetId(UUID fileDatasetId);
}
