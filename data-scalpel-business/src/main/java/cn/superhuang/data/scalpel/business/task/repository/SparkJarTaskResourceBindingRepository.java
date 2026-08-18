package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskResourceBinding;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface SparkJarTaskResourceBindingRepository extends JpaRepository<SparkJarTaskResourceBinding, UUID> {
    List<SparkJarTaskResourceBinding> findAllByTaskIdOrderByCreatedAtAsc(UUID taskId);
    List<SparkJarTaskResourceBinding> findAllByTaskIdInOrderByTaskIdAscCreatedAtAsc(Collection<UUID> taskIds);
    List<SparkJarTaskResourceBinding> findAllByResourceTypeAndResourceIdOrderByTaskIdAscBindingNameAsc(
            SparkJarResourceType resourceType, UUID resourceId);
    List<SparkJarTaskResourceBinding> findAllByTaskIdInAndResourceTypeAndResourceIdOrderByTaskIdAscBindingNameAsc(
            Collection<UUID> taskIds, SparkJarResourceType resourceType, UUID resourceId);
    boolean existsByResourceTypeAndResourceId(SparkJarResourceType resourceType, UUID resourceId);
    void deleteAllByTaskId(UUID taskId);
}
