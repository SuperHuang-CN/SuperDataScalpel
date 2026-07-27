package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDatasetParseJobRepository extends SearchRepository<FileDatasetParseJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from FileDatasetParseJob job where job.id = :id")
    Optional<FileDatasetParseJob> findLockedById(@Param("id") UUID id);

    Optional<FileDatasetParseJob> findByIdAndFileDatasetId(UUID id, UUID fileDatasetId);

    List<FileDatasetParseJob> findByFileDatasetTableIdOrderByCreatedAtDesc(UUID fileDatasetTableId);

    boolean existsByFileDatasetTableIdAndStatusIn(
            UUID fileDatasetTableId,
            Collection<FileDatasetParseJobStatus> statuses
    );

    boolean existsByFileDatasetIdAndStatusIn(
            UUID fileDatasetId,
            Collection<FileDatasetParseJobStatus> statuses
    );

    boolean existsBySourceFileIdAndStatusIn(
            UUID sourceFileId,
            Collection<FileDatasetParseJobStatus> statuses
    );

    List<FileDatasetParseJob> findBySourceFileIdAndStatusIn(
            UUID sourceFileId,
            Collection<FileDatasetParseJobStatus> statuses
    );

    long countByStatus(FileDatasetParseJobStatus status);

    long countByStatusAndAvailableAtLessThanEqual(FileDatasetParseJobStatus status, Instant availableAt);

    @Query("select min(job.queuedAt) from FileDatasetParseJob job where job.status = :status")
    Optional<Instant> findOldestQueuedAt(@Param("status") FileDatasetParseJobStatus status);

    @Query("select min(job.startedAt) from FileDatasetParseJob job where job.status = :status")
    Optional<Instant> findOldestStartedAt(@Param("status") FileDatasetParseJobStatus status);

    @Query("""
            select job.id
            from FileDatasetParseJob job
            where job.status in :statuses
              and job.completedAt < :cutoff
            order by job.completedAt, job.id
            """)
    List<UUID> findExpiredTerminalIds(
            @Param("statuses") Collection<FileDatasetParseJobStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );
}
