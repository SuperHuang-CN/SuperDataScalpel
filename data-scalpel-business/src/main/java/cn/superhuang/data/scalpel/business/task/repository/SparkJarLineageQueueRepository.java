package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageIngestion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SparkJarLineageQueueRepository {
    @PersistenceContext private EntityManager entityManager;

    public Optional<SparkJarLineageIngestion> lockNext(Instant now) {
        List<?> rows = entityManager.createNativeQuery("""
                select job.* from task_spark_jar_lineage_ingestion job
                where job.status = 'QUEUED' and job.available_at <= :now
                order by job.available_at, job.created_at, job.id
                limit 1 for update skip locked
                """, SparkJarLineageIngestion.class).setParameter("now", now).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((SparkJarLineageIngestion) rows.getFirst());
    }

    public List<SparkJarLineageIngestion> lockExpired(Instant now) {
        return entityManager.createNativeQuery("""
                select job.* from task_spark_jar_lineage_ingestion job
                where job.status = 'RUNNING' and job.lease_expires_at <= :now
                order by job.lease_expires_at limit 100 for update skip locked
                """, SparkJarLineageIngestion.class).setParameter("now", now).getResultList();
    }
}
