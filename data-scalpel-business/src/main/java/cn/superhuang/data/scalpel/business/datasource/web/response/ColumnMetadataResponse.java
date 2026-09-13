package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据库表列元数据及平台类型映射结果")
public record ColumnMetadataResponse(
        @Schema(description = "数据库物理列名。") String name,
        @Schema(description = "列在表中的顺序，从数据库元数据定义的序号开始") int ordinal,
        @Schema(description = "java.sql.Types 定义的 JDBC 类型数值，仅用于数据库元数据边界") int jdbcType,
        @Schema(description = "数据库声明的原生类型名称") String nativeType,
        @Schema(description = "方言归一化后的逻辑类型") String logicalType,
        @Schema(description = "可接受的平台类型定义；类型无法安全映射时为空") PlatformTypeDefinition platformTypeDefinition,
        @Schema(description = "CHAR、VARCHAR、NCHAR 或 NVARCHAR 列的最大长度；数据库未报告或其他类型为空") Integer length,
        @Schema(description = "数值列的总有效位数；数据库未报告或非精确数值类型为空") Integer precision,
        @Schema(description = "数值列的小数位数；数据库未报告或非精确数值类型为空") Integer scale,
        @Schema(description = "数据库列是否允许 NULL") boolean nullable,
        @Schema(description = "数据库声明的默认值表达式；未声明时为空") String defaultValue,
        @Schema(description = "是否由数据库自动递增") boolean autoIncrement,
        @Schema(description = "是否为数据库生成列") boolean generated,
        @Schema(description = "数据库列注释；未配置时为空。") String comment,
        @Schema(description = "列角色，例如普通列、时间列或标签列；由数据库方言识别") String role
) {
    static ColumnMetadataResponse from(ColumnMetadata column, DatabaseDialect dialect) {
        var mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(column));
        return new ColumnMetadataResponse(
                column.name(), column.ordinal(), column.jdbcType(), column.nativeType(), column.logicalType().name(),
                mapping.acceptable() ? mapping.definition() : null,
                column.length(), column.precision(), column.scale(), column.nullable(), column.defaultValue(),
                column.autoIncrement(), column.generated(), column.comment(), column.role().name()
        );
    }
}
