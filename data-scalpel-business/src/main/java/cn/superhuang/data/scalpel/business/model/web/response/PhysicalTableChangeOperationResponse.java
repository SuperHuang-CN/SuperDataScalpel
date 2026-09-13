package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "目标字段结构与原结构之间的一项物理变化")
public record PhysicalTableChangeOperationResponse(
        @Schema(description = "变更类型，例如新增、删除、改名、改类型、改可空性、改主键或存储定义") TableChangeOperationType type,
        @Schema(description = "变更前列快照；新增列时为空") PhysicalTableChangeColumnResponse beforeColumn,
        @Schema(description = "变更后列快照；删除列时为空") PhysicalTableChangeColumnResponse afterColumn,
        @Schema(description = "变更前主键列及顺序；非主键变更时为空列表") List<String> beforePrimaryKeyColumns,
        @Schema(description = "变更后主键列及顺序；非主键变更时为空列表") List<String> afterPrimaryKeyColumns,
        @Schema(description = "该操作需要的执行策略") TableChangeStrategy strategy,
        @Schema(description = "该操作最高风险：SAFE、CAUTION 或 DESTRUCTIVE") TableChangeRisk risk,
        @Schema(description = "策略与风险的结构化原因") List<PhysicalTableChangeReasonResponse> reasons,
        @Schema(description = "执行该操作前必须通过的数据或结构检查") List<PhysicalTableChangeCheckResponse> checks
) {
    static PhysicalTableChangeOperationResponse from(TableChangeOperation operation) {
        return new PhysicalTableChangeOperationResponse(
                operation.type(),
                PhysicalTableChangeColumnResponse.from(operation.beforeColumn()),
                PhysicalTableChangeColumnResponse.from(operation.afterColumn()),
                operation.beforePrimaryKeyColumns(), operation.afterPrimaryKeyColumns(), operation.strategy(), operation.risk(),
                operation.reasons().stream().map(PhysicalTableChangeReasonResponse::from).toList(),
                operation.checks().stream().map(PhysicalTableChangeCheckResponse::from).toList()
        );
    }
}
