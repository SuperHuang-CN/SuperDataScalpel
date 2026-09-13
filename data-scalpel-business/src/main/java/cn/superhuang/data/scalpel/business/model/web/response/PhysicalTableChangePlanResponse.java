package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "方言基于真实物理表和目标字段生成并冻结的结构变更计划")
public record PhysicalTableChangePlanResponse(
        @Schema(description = "计划总体策略：IN_PLACE、REBUILD_RECOMMENDED、REBUILD_REQUIRED 或 UNSUPPORTED") TableChangeStrategy strategy,
        @Schema(description = "计划最高风险：SAFE、CAUTION 或 DESTRUCTIVE，不低于任一子操作") TableChangeRisk risk,
        @Schema(description = "所提供执行方案的总体 DDL 原子性") TableDdlAtomicity atomicity,
        @Schema(description = "生成计划时真实物理表的规范化结构 SHA-256；执行前必须再次匹配") String beforeFingerprint,
        @Schema(description = "目标字段结构的规范化 SHA-256；执行后必须严格匹配") String targetFingerprint,
        @Schema(description = "计划是否提供可执行的原表修改方案") boolean allowsInPlaceExecution,
        @Schema(description = "计划是否提供可执行的受控重建方案") boolean allowsRebuildExecution,
        @Schema(description = "方言明确提供的执行方式和冻结 SQL；空列表表示当前计划不可执行") List<PhysicalTableChangeExecutionOptionResponse> executionOptions,
        @Schema(description = "从原结构到目标结构的逐项变化") List<PhysicalTableChangeOperationResponse> operations,
        @Schema(description = "执行整个计划前必须通过的计划级检查") List<PhysicalTableChangeCheckResponse> checks,
        @Schema(description = "总体策略、风险、原子性或不支持原因") List<PhysicalTableChangeReasonResponse> reasons
) {
    public static PhysicalTableChangePlanResponse from(TableChangePlan plan) {
        return new PhysicalTableChangePlanResponse(
                plan.strategy(), plan.risk(), plan.atomicity(), plan.beforeFingerprint().value(), plan.targetFingerprint().value(),
                plan.allowsInPlaceExecution(), plan.allowsRebuildExecution(),
                plan.executionOptions().stream().map(PhysicalTableChangeExecutionOptionResponse::from).toList(),
                plan.operations().stream().map(PhysicalTableChangeOperationResponse::from).toList(),
                plan.checks().stream().map(PhysicalTableChangeCheckResponse::from).toList(),
                plan.reasons().stream().map(PhysicalTableChangeReasonResponse::from).toList()
        );
    }
}
