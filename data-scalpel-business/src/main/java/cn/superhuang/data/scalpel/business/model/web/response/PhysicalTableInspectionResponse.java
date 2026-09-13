package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "模型物理表存在性和结构的实时检查结果")
public record PhysicalTableInspectionResponse(
        @Schema(description = "MANAGED 受管表或 EXTERNAL 外部表") PhysicalTableMode mode,
        @Schema(description = "综合状态：NOT_FOUND 不存在、MATCHED 匹配、DRIFTED 漂移、UNREACHABLE 无法连接或 UNSUPPORTED 不支持检查") PhysicalTableState state,
        @Schema(description = "解析后的物理 Catalog；数据库不使用 Catalog 时为空") String catalogName,
        @Schema(description = "解析后的物理 Schema；数据库不使用 Schema 时为空") String schemaName,
        @Schema(description = "实时检查实际定位到的物理表名") String tableName,
        @Schema(description = "目标物理表是否存在") boolean exists,
        @Schema(description = "真实物理结构是否严格匹配当前模型契约") boolean compatible,
        @Schema(description = "当前方言是否支持按模型契约创建缺失的 MANAGED 物理表") boolean createSupported,
        @Schema(description = "检查结论或下一步处理说明") String message,
        @Schema(description = "结构不匹配时的逐项差异；匹配或不存在时为空列表") List<PhysicalTableDifferenceResponse> differences
) {
    public static PhysicalTableInspectionResponse from(PhysicalTableMode mode, ModelPhysicalTableInspection inspection) {
        return new PhysicalTableInspectionResponse(
                mode,
                inspection.state(),
                inspection.table().catalog(),
                inspection.table().schema(),
                inspection.table().table(),
                inspection.exists(),
                inspection.compatible(),
                inspection.createSupported(),
                inspection.message(),
                inspection.differences().stream().map(PhysicalTableDifferenceResponse::from).toList()
        );
    }
}
