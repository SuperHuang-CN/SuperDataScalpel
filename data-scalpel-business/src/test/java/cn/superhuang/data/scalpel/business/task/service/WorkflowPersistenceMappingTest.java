package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.WorkflowTaskDefinition;
import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Collection;

/** Validate the new HQL/mappings without opening any database connection. */
class WorkflowPersistenceMappingTest {
    @Test void validatesParentAssociationsAndSubmissionOrderingQuery() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(TaskRun.class)
                .addAnnotatedClass(WorkflowTaskDefinition.class).addAnnotatedClass(TaskExecutionOutboxMessage.class)
                .buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
            String hql = TaskExecutionOutboxRepository.class.getMethod("findDueForUpdate", Collection.class, Instant.class, Pageable.class)
                    .getAnnotation(Query.class).value();
            session.createSelectionQuery(hql, TaskExecutionOutboxMessage.class);
            session.createSelectionQuery("from TaskRun r where r.parentRunId = :parentId order by r.queuedAt", TaskRun.class);
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
