package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitJob;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SparkJarDevelopmentKitQueueRepository {
    @PersistenceContext private EntityManager entityManager;

    public Optional<SparkJarDevelopmentKitJob> lockNext(Instant now) {
        List<?> rows = entityManager.createNativeQuery("""
                select job.* from task_spark_jar_development_kit_job job
                where job.status = 'QUEUED' and job.available_at <= :now
                order by job.available_at, job.created_at, job.id
                limit 1 for update skip locked
                """, SparkJarDevelopmentKitJob.class).setParameter("now", now).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((SparkJarDevelopmentKitJob) rows.getFirst());
    }

    public List<SparkJarDevelopmentKitJob> lockExpired(Instant now) {
        return entityManager.createNativeQuery("""
                select job.* from task_spark_jar_development_kit_job job
                where job.status = 'RUNNING' and job.lease_expires_at <= :now
                order by job.lease_expires_at limit 100 for update skip locked
                """, SparkJarDevelopmentKitJob.class).setParameter("now", now).getResultList();
    }
}
