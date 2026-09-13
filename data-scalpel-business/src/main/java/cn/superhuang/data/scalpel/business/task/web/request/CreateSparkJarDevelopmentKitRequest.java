package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitSampleMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "基于已保存的批处理或流式 Spark JAR 任务定义异步生成本地开发套件。套件可包含真实输入样本且不会自动脱敏；调用方应按数据权限和最小数据原则选择采样模式。提交会保存本次套件配置，但不改变生产任务定义版本。")
public record CreateSparkJarDevelopmentKitRequest(
        @Schema(description = "客户端读取到的当前 Spark JAR 任务定义版本，必须大于等于 1且与服务端完全一致；不一致返回 409，避免套件配置套用到变化后的资源绑定。") @Min(1) int definitionVersion,
        @Schema(description = "任务可读 MODEL 绑定的采样配置。省略或未列出的每个可读模型绑定都会规范化为 ROW_COUNT 1000；显式 NONE 才表示不导出该绑定样本。重复、不可读或不存在的绑定被拒绝。") List<@Valid InputSample> samples,
        @Schema(description = "仅加入开发套件的额外 JDBC 物理表采样；每项必须基于任务定义中已有的可读 JDBC_DATA_SOURCE 绑定，不新增权限，也不写回生产资源绑定。省略为空列表。") List<@Valid JdbcTableSample> jdbcTables
) {
    public CreateSparkJarDevelopmentKitRequest {
        samples = samples == null ? List.of() : List.copyOf(samples);
        jdbcTables = jdbcTables == null ? List.of() : List.copyOf(jdbcTables);
    }

    @Schema(description = "任务输入绑定的本地样本选择")
    public record InputSample(
            @Schema(description = "任务定义中的 MODEL 资源绑定名称，去除首尾空白后必须精确匹配已有可读绑定；同一名称只能出现一次。") @NotBlank @Size(max = 100) String bindingName,
            @Schema(description = "采样模式：NONE 不采样、ROW_COUNT 固定行数、PERCENTAGE 百分比、ALL 全量；大数据应避免 ALL") @NotNull SparkJarDevelopmentKitSampleMode mode,
            @Schema(description = "ROW_COUNT 模式必填的最大样本行数，范围 1 到 1,000,000；其他模式必须为空。") @Min(1) @Max(1_000_000) Integer rowCount,
            @Schema(description = "PERCENTAGE 模式必填的采样百分比，范围 0.01 到 100；其他模式必须为空。") @DecimalMin("0.01") @DecimalMax("100") BigDecimal percentage
    ) {}

    /** A physical JDBC table included only in this generated local development kit. */
    @Schema(description = "仅供本地开发套件使用的额外 JDBC 物理表样本，不新增任务资源权限")
    public record JdbcTableSample(
            @Schema(description = "任务定义中已有的可读 JDBC_DATA_SOURCE 绑定名称；它决定连接和权限，同一绑定可声明多个不同物理表。") @NotBlank @Size(max = 100) String bindingName,
            @Schema(description = "物理表 Catalog；数据库不使用时为空") @Size(max = 255) String catalog,
            @Schema(description = "物理表 Schema；数据库不使用时为空") @Size(max = 255) String schema,
            @Schema(description = "要抽取样本的物理表名称；与 catalog、schema 共同定位目标表。") @NotBlank @Size(max = 255) String table,
            @Schema(description = "采样模式：NONE 不导出、ROW_COUNT 固定上限、PERCENTAGE 百分比、ALL 全量。") @NotNull SparkJarDevelopmentKitSampleMode mode,
            @Schema(description = "ROW_COUNT 模式必填的最大样本行数，范围 1 到 1,000,000；其他模式必须为空。") @Min(1) @Max(1_000_000) Integer rowCount,
            @Schema(description = "PERCENTAGE 模式必填的采样百分比，范围 0.01 到 100；其他模式必须为空。") @DecimalMin("0.01") @DecimalMax("100") BigDecimal percentage
    ) {}
}
