package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "变更计划中的一列结构快照")
public record PhysicalTableChangeColumnResponse(
        @Schema(description = "模型字段 UUID，用于变更前后列身份配对；不参与物理结构指纹") UUID columnId,
        @Schema(description = "变更计划冻结的目标物理列名") String name,
        @Schema(description = "方言无关的列类型") TableColumnType type,
        @Schema(description = "目标 STRING 或 BINARY 物理列的最大长度；其他类型为空") Integer length,
        @Schema(description = "目标 DECIMAL 物理列的总有效位数；其他类型为空") Integer precision,
        @Schema(description = "目标 DECIMAL 物理列的小数位数；其他类型为空") Integer scale,
        @Schema(description = "Geometry 定义；标量列为空") GeometryTypeDefinition geometry,
        @Schema(description = "物理列是否允许 NULL") boolean nullable
) {
    static PhysicalTableChangeColumnResponse from(TableColumnDefinition column) {
        if (column == null) {
            return null;
        }
        return new PhysicalTableChangeColumnResponse(
                column.columnId(), column.name(), column.type(), column.length(), column.precision(), column.scale(),
                column.geometry(), column.nullable()
        );
    }
}
