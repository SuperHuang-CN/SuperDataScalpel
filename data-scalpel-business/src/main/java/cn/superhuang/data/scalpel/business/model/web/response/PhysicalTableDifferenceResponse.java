package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "模型期望结构与真实物理表之间的一项严格差异")
public record PhysicalTableDifferenceResponse(
        @Schema(description = "相关物理列名；表级存储配置差异时可为空") String column,
        @Schema(description = "差异类型，例如缺少/多余列、类型、长度、精度、可空性、主键或存储配置") TableStructureDifferenceType type,
        @Schema(description = "模型契约期望值") String expected,
        @Schema(description = "数据库实际值") String actual
) {
    static PhysicalTableDifferenceResponse from(TableStructureDifference difference) {
        return new PhysicalTableDifferenceResponse(
                difference.column(), difference.type(), difference.expected(), difference.actual()
        );
    }
}
