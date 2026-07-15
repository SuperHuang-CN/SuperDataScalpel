package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;

import java.util.List;

public record PhysicalTableChangeOperationResponse(
        TableChangeOperationType type,
        PhysicalTableChangeColumnResponse beforeColumn,
        PhysicalTableChangeColumnResponse afterColumn,
        List<String> beforePrimaryKeyColumns,
        List<String> afterPrimaryKeyColumns,
        TableChangeStrategy strategy,
        TableChangeRisk risk,
        List<PhysicalTableChangeReasonResponse> reasons,
        List<PhysicalTableChangeCheckResponse> checks
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
