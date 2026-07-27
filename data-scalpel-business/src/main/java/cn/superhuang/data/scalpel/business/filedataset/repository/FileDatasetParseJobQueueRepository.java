package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** PostgreSQL-backed queue reads that lock candidates without blocking competing workers. */
@Repository
public class FileDatasetParseJobQueueRepository {

    private static final String CLAIM_SQL = """
            select job.*
            from ds_file_dataset_parse_job job
            where job.status = 'QUEUED'
              and job.available_at <= :now
              and not exists (
                  select 1
                  from ds_file_dataset_parse_job running
                  where running.source_file_id = job.source_file_id
                    and running.status = 'RUNNING'
              )
            order by job.available_at, job.created_at, job.id
            limit 1
            for update skip locked
            """;

    private static final String RECOVERY_SQL = """
            select job.*
            from ds_file_dataset_parse_job job
            where job.status = 'RUNNING'
              and job.lease_expires_at <= :now
            order by job.lease_expires_at, job.created_at, job.id
            limit 100
            for update skip locked
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<FileDatasetParseJob> lockNextAvailable(Instant now) {
        List<?> rows = entityManager.createNativeQuery(CLAIM_SQL, FileDatasetParseJob.class)
                .setParameter("now", now)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(FileDatasetParseJob.class.cast(rows.getFirst()));
    }

    public List<FileDatasetParseJob> lockExpiredLeases(Instant now) {
        List<?> rows = entityManager.createNativeQuery(RECOVERY_SQL, FileDatasetParseJob.class)
                .setParameter("now", now)
                .getResultList();
        return rows.stream()
                .map(FileDatasetParseJob.class::cast)
                .toList();
    }
}
