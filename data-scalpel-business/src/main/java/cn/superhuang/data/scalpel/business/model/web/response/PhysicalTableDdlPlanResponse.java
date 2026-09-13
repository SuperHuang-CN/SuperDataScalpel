package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "根据当前模型字段生成的受控建表 DDL 预览；读取该接口不会执行 SQL")
public record PhysicalTableDdlPlanResponse(
        @Schema(description = "模型物理表模式；只有 MANAGED 模式可能支持建表") PhysicalTableMode mode,
        @Schema(description = "当前数据源方言、运行能力和字段结构是否支持平台建表") boolean supported,
        @Schema(description = "建表能力判断或不支持原因") String message,
        @Schema(description = "方言生成的只读建表 SQL；不支持时为空列表，客户端不能修改后回传执行") List<String> statements
) {
    public static PhysicalTableDdlPlanResponse supported(PhysicalTableMode mode, DdlPlan plan) {
        return new PhysicalTableDdlPlanResponse(mode, true, "建表 SQL 已根据当前模型字段生成", plan.statements());
    }

    public static PhysicalTableDdlPlanResponse unsupported(PhysicalTableMode mode, String message) {
        return new PhysicalTableDdlPlanResponse(mode, false, message, List.of());
    }
}
