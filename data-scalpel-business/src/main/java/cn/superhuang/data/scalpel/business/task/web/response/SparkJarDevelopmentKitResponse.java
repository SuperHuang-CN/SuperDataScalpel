package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitSampleMode;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStage;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The only user-visible development kit for one Spark JAR task. */
@Schema(description = "批处理或流式 Spark JAR 任务的当前本地开发套件配置、最近生成请求和当前可下载制品。生成流程与生产任务执行相互独立。")
public record SparkJarDevelopmentKitResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "查询时当前 Spark JAR 任务定义版本；用于下一次生成请求的并发校验，不是 generation 或 artifact 必然使用的版本。")
        int definitionVersion,
        @Schema(description = "当前已保存并按现有资源绑定重新规范化的采样配置；新增可读 MODEL 绑定会以 ROW_COUNT 1000 出现在这里，已删除或失去读取权限的绑定会被过滤。")
        Configuration configuration,
        @Schema(description = "最近一次生成进度；尚未请求生成时为空。")
        Generation generation,
        @Schema(description = "最近成功生成的 ZIP 制品；没有可用制品时为空。")
        Artifact artifact
) {
    @Schema(description = "生成开发套件时使用的输入模型和 JDBC 表采样配置。")
    public record Configuration(
            @Schema(description = "输入模型绑定的样本配置列表。")
            List<InputSample> samples,
            @Schema(description = "JDBC 表资源绑定的样本配置列表。")
            List<JdbcTableSample> jdbcTables
    ) {
        public Configuration {
            samples = List.copyOf(samples);
            jdbcTables = List.copyOf(jdbcTables);
        }
    }

    @Schema(description = "一个已绑定输入模型的样本配置。")

    public record InputSample(
            @Schema(description = "JAR 代码使用的资源绑定名，在单个任务定义内唯一。")
            String bindingName,
            @Schema(description = "采样模式，决定是否以及如何导出样本数据。")
            SparkJarDevelopmentKitSampleMode mode,
            @Schema(description = "ROW_COUNT 模式的最大样本行数；其他模式为空。")
            Integer rowCount,
            @Schema(description = "PERCENTAGE 模式的采样百分比；其他模式为空。")
            BigDecimal percentage
    ) {}

    @Schema(description = "一个已绑定 JDBC 表的样本配置。")

    public record JdbcTableSample(
            @Schema(description = "JAR 代码使用的资源绑定名，在单个任务定义内唯一。")
            String bindingName,
            @Schema(description = "JDBC 表目录名；数据源不支持目录时为空。")
            String catalog,
            @Schema(description = "JDBC Schema 名；数据源不支持时为空。")
            String schema,
            @Schema(description = "JDBC 物理表名。")
            String table,
            @Schema(description = "采样模式，决定是否以及如何导出样本数据。")
            SparkJarDevelopmentKitSampleMode mode,
            @Schema(description = "ROW_COUNT 模式的最大样本行数；其他模式为空。")
            Integer rowCount,
            @Schema(description = "PERCENTAGE 模式的采样百分比；其他模式为空。")
            BigDecimal percentage
    ) {}

    @Schema(description = "最近一次开发套件生成请求的进度和结果。")

    public record Generation(
            @Schema(description = "开发套件生成请求 UUID，用于查询本次生成进度和制品。")
            UUID id,
        @Schema(description = "该次生成请求创建时固定的 Spark JAR 任务定义版本；当前定义随后变化不会改写。")
            int definitionVersion,
            @Schema(description = "开发套件生成状态：QUEUED 排队，RUNNING 生成中，SUCCEEDED 成功，FAILED 失败，EXPIRED 请求或制品已过期。")
            SparkJarDevelopmentKitStatus status,
            @Schema(description = "生成流程当前阶段。")
            SparkJarDevelopmentKitStage stage,
            @Schema(description = "生成进度百分比，范围 0 到 100。")
            int progressPercent,
            @Schema(description = "当前正在导出的模型名称；非模型阶段为空。")
            String currentModel,
            @Schema(description = "该生成请求已被工作线程领取的次数，最多 3 次；可重试失败会延迟后重新排队。")
            int attemptCount,
            @Schema(description = "稳定错误码；未失败时为空。")
            String errorCode,
            @Schema(description = "安全错误摘要；未失败时为空。")
            String errorMessage,
            @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
            Instant createdAt,
            @Schema(description = "实际开始时间；尚未开始时为空。")
            Instant startedAt,
            @Schema(description = "完成时间；尚未进入终态时为空。")
            Instant completedAt
    ) {}

    @Schema(description = "已生成开发套件制品的完整性信息。")

    public record Artifact(
            @Schema(description = "ZIP 制品大小，单位字节。")
            long sizeBytes,
            @Schema(description = "文件内容的 SHA-256 十六进制摘要。")
            String sha256,
            @Schema(description = "制品生成时间。")
            Instant generatedAt,
            @Schema(description = "该制品生成请求的采样配置 JSON 是否与当前保存配置完全相同；此字段不比较当前任务 definitionVersion，当前响应也不直接暴露制品来源版本，因此 true 不能证明制品与当前任务定义完全一致。")
            boolean matchesSavedConfiguration
    ) {}
}
