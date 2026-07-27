package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskStreamingConfigurationRepository extends JpaRepository<TaskStreamingConfiguration, UUID> {
    Optional<TaskStreamingConfiguration> findByTaskId(UUID taskId);
    void deleteByTaskId(UUID taskId);
}
