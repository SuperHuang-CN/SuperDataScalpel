package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_spark_jar_resource_binding", uniqueConstraints =
        @UniqueConstraint(name = "uk_task_spark_jar_binding_name", columnNames = {"task_id", "binding_name"}),
        indexes = {
                @Index(name = "idx_task_spark_jar_binding_resource", columnList = "resource_type,resource_id"),
                @Index(name = "idx_task_spark_jar_binding_task", columnList = "task_id")
        })
public class SparkJarTaskResourceBinding extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "binding_name", nullable = false, length = 100, updatable = false)
    private String bindingName;
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32, updatable = false)
    private SparkJarResourceType resourceType;
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;
    @Column(name = "topic_name", length = 249, updatable = false)
    private String topicName;
    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false, length = 16, updatable = false)
    private SparkJarResourceAccessMode accessMode;

    protected SparkJarTaskResourceBinding() {}

    public static SparkJarTaskResourceBinding create(UUID taskId, String bindingName,
                                                      SparkJarResourceType resourceType, UUID resourceId,
                                                      SparkJarResourceAccessMode accessMode) {
        return create(taskId, bindingName, resourceType, resourceId, null, accessMode);
    }

    public static SparkJarTaskResourceBinding create(UUID taskId, String bindingName,
                                                      SparkJarResourceType resourceType, UUID resourceId,
                                                      String topicName,
                                                      SparkJarResourceAccessMode accessMode) {
        SparkJarTaskResourceBinding binding = new SparkJarTaskResourceBinding();
        binding.taskId = Objects.requireNonNull(taskId);
        binding.bindingName = Objects.requireNonNull(bindingName).trim();
        binding.resourceType = Objects.requireNonNull(resourceType);
        binding.resourceId = Objects.requireNonNull(resourceId);
        binding.topicName = resourceType == SparkJarResourceType.KAFKA_TOPIC
                ? Objects.requireNonNull(topicName).trim() : null;
        binding.accessMode = Objects.requireNonNull(accessMode);
        return binding;
    }

    public UUID getTaskId() { return taskId; }
    public String getBindingName() { return bindingName; }
    public SparkJarResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getTopicName() { return topicName; }
    public SparkJarResourceAccessMode getAccessMode() { return accessMode; }
}
