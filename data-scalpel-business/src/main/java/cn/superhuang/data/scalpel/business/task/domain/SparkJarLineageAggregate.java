package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_spark_jar_lineage_aggregate", uniqueConstraints =
        @UniqueConstraint(name = "uk_spark_jar_lineage_aggregate_identity",
                columnNames = {"task_id", "definition_version", "jar_sha256"}), indexes =
        @Index(name = "idx_spark_jar_lineage_aggregate_task", columnList = "task_id,definition_version"))
public class SparkJarLineageAggregate extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "definition_version", nullable = false, updatable = false) private int definitionVersion;
    @Column(name = "jar_sha256", nullable = false, length = 64, updatable = false) private String jarSha256;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "evidence_json", nullable = false) private String evidenceJson;
    @Column(name = "content_sha256", nullable = false, length = 64) private String contentSha256;

    protected SparkJarLineageAggregate() {
    }

    public static SparkJarLineageAggregate create(
            UUID taskId, int definitionVersion, String jarSha256, String evidenceJson, String contentSha256
    ) {
        SparkJarLineageAggregate value = new SparkJarLineageAggregate();
        value.taskId = Objects.requireNonNull(taskId);
        value.definitionVersion = definitionVersion;
        value.jarSha256 = Objects.requireNonNull(jarSha256);
        value.replace(evidenceJson, contentSha256);
        return value;
    }

    public void replace(String evidenceJson, String contentSha256) {
        this.evidenceJson = Objects.requireNonNull(evidenceJson);
        this.contentSha256 = Objects.requireNonNull(contentSha256);
    }

    public String getEvidenceJson() { return evidenceJson; }
    public String getContentSha256() { return contentSha256; }
}
