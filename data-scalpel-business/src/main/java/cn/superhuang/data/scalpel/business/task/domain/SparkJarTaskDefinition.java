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

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "execution_resources_json")
    private String executionResourcesJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "development_kit_config_json")
    private String developmentKitConfigJson;
    @Column(name = "current_development_kit_job_id")
    private UUID currentDevelopmentKitJobId;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "online_source_code")
    private String onlineSourceCode;
    @Column(name = "online_compiled_source_sha256", length = 64)
    private String onlineCompiledSourceSha256;
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

    public boolean updateConfiguration(
            String parametersJson,
            String sparkConfJson,
            String executionResourcesJson,
            int timeoutSeconds
    ) {
        if (timeoutSeconds < 1 || timeoutSeconds > 86400) {
            throw new IllegalArgumentException("超时时间必须在 1 到 86400 秒之间");
        }
        if (Objects.equals(this.parametersJson, parametersJson)
                && Objects.equals(this.sparkConfJson, sparkConfJson)
                && Objects.equals(this.executionResourcesJson, executionResourcesJson)
                && this.timeoutSeconds == timeoutSeconds) {
            return false;
        }
        this.parametersJson = Objects.requireNonNull(parametersJson);
        this.sparkConfJson = Objects.requireNonNull(sparkConfJson);
        this.executionResourcesJson = executionResourcesJson;
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

    /** Authoring drafts are persisted independently from the production definition version. */
    public void saveOnlineSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) throw new IllegalArgumentException("在线源码不能为空");
        this.onlineSourceCode = sourceCode;
    }

    public void markOnlineSourceCompiled(String sourceSha256) {
        this.onlineCompiledSourceSha256 = required(sourceSha256);
    }

    /** A manually uploaded JAR becomes authoritative without discarding the saved online draft. */
    public void markJarUploaded() {
        this.onlineCompiledSourceSha256 = null;
    }

    public void resourceBindingsChanged() { incrementVersion(); }

    /**
     * Local-development-kit settings are auxiliary authoring data.  They must not
     * change the production Spark JAR definition version.
     */
    public void saveDevelopmentKitConfig(String configJson) {
        this.developmentKitConfigJson = required(configJson);
    }

    /** Returns the replaced current package, if there was one. */
    public UUID replaceCurrentDevelopmentKit(UUID jobId) {
        UUID previous = currentDevelopmentKitJobId;
        currentDevelopmentKitJobId = Objects.requireNonNull(jobId);
        return previous;
    }

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
    public String getExecutionResourcesJson() { return executionResourcesJson; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getVersion() { return version; }
    public String getDevelopmentKitConfigJson() { return developmentKitConfigJson; }
    public UUID getCurrentDevelopmentKitJobId() { return currentDevelopmentKitJobId; }
    public String getOnlineSourceCode() { return onlineSourceCode; }
    public String getOnlineCompiledSourceSha256() { return onlineCompiledSourceSha256; }
    public boolean hasJar() { return jarObjectKey != null; }
}
