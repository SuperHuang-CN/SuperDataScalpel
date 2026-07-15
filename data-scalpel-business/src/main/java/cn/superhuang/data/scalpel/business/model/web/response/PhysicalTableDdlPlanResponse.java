package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;

import java.util.List;

public record PhysicalTableDdlPlanResponse(
        PhysicalTableMode mode,
        boolean supported,
        String message,
        List<String> statements
) {
    public static PhysicalTableDdlPlanResponse supported(PhysicalTableMode mode, DdlPlan plan) {
        return new PhysicalTableDdlPlanResponse(mode, true, "建表 SQL 已根据当前模型字段生成", plan.statements());
    }

    public static PhysicalTableDdlPlanResponse unsupported(PhysicalTableMode mode, String message) {
        return new PhysicalTableDdlPlanResponse(mode, false, message, List.of());
    }
}
