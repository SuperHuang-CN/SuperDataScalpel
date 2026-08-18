package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_spark_jar_definition", uniqueConstraints =
        @UniqueConstraint(name = "uk_task_spark_jar_definition_task", columnNames = "task_id"))
public class SparkJarTaskDefinition extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "jar_object_key", length = 500)
    private String jarObjectKey;
    @Column(name = "jar_file_name", length = 255)
    private String jarFileName;
    @Column(name = "jar_sha256", length = 64)
    private String jarSha256;
    @Column(name = "jar_size_bytes")
    private Long jarSizeBytes;
    @Column(name = "job_class", length = 500)
    private String jobClass;
    @Column(name = "job_api_version")
    private Integer jobApiVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_mode", nullable = false, length = 16)
    private SparkJarJobMode jobMode;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "parameters_json", nullable = false)
    private String parametersJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "spark_conf_json", nullable = false)
    private String sparkConfJson;
    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;
    @Column(nullable = false)
    private int version;

    protected SparkJarTaskDefinition() {}

    private SparkJarTaskDefinition(UUID taskId, SparkJarJobMode jobMode) {
        this.taskId = Objects.requireNonNull(taskId);
        this.jobMode = Objects.requireNonNull(jobMode);
        this.parametersJson = "[]";
        this.sparkConfJson = "[]";
        this.timeoutSeconds = 3600;
        this.version = 1;
    }

    public static SparkJarTaskDefinition create(UUID taskId) {
        return new SparkJarTaskDefinition(taskId, SparkJarJobMode.BATCH);
    }

    public static SparkJarTaskDefinition create(UUID taskId, SparkJarJobMode jobMode) {
        return new SparkJarTaskDefinition(taskId, jobMode);
    }

    public boolean updateConfiguration(String parametersJson, String sparkConfJson, int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 86400) {
            throw new IllegalArgumentException("超时时间必须在 1 到 86400 秒之间");
        }
        if (Objects.equals(this.parametersJson, parametersJson)
                && Objects.equals(this.sparkConfJson, sparkConfJson)
                && this.timeoutSeconds == timeoutSeconds) {
            return false;
        }
        this.parametersJson = Objects.requireNonNull(parametersJson);
        this.sparkConfJson = Objects.requireNonNull(sparkConfJson);
        this.timeoutSeconds = timeoutSeconds;
        incrementVersion();
        return true;
    }

    public boolean updateJar(String objectKey, String fileName, String sha256, long sizeBytes,
                             String jobClass, int jobApiVersion, SparkJarJobMode jobMode) {
        if (Objects.equals(this.jarObjectKey, objectKey) && Objects.equals(this.jarFileName, fileName)
                && Objects.equals(this.jarSha256, sha256) && Objects.equals(this.jarSizeBytes, sizeBytes)
                && Objects.equals(this.jobClass, jobClass) && Objects.equals(this.jobApiVersion, jobApiVersion)
                && this.jobMode == jobMode) {
            return false;
        }
        this.jarObjectKey = required(objectKey);
        this.jarFileName = required(fileName);
        this.jarSha256 = required(sha256);
        this.jarSizeBytes = sizeBytes;
        this.jobClass = required(jobClass);
        this.jobApiVersion = jobApiVersion;
        this.jobMode = Objects.requireNonNull(jobMode);
        incrementVersion();
        return true;
    }

    public void resourceBindingsChanged() { incrementVersion(); }

    private void incrementVersion() {
        if (version == Integer.MAX_VALUE) throw new IllegalStateException("Spark JAR 定义版本已达到最大值");
        version++;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Spark JAR 元数据不能为空");
        return value.trim();
    }

    public UUID getTaskId() { return taskId; }
    public String getJarObjectKey() { return jarObjectKey; }
    public String getJarFileName() { return jarFileName; }
    public String getJarSha256() { return jarSha256; }
    public Long getJarSizeBytes() { return jarSizeBytes; }
    public String getJobClass() { return jobClass; }
    public Integer getJobApiVersion() { return jobApiVersion; }
    public SparkJarJobMode getJobMode() { return jobMode; }
    public String getParametersJson() { return parametersJson; }
    public String getSparkConfJson() { return sparkConfJson; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getVersion() { return version; }
    public boolean hasJar() { return jarObjectKey != null; }
}
