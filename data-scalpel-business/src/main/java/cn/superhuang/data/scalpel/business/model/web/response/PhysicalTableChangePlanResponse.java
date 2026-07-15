package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;

import java.util.List;

public record PhysicalTableChangePlanResponse(
        TableChangeStrategy strategy,
        TableChangeRisk risk,
        TableDdlAtomicity atomicity,
        String beforeFingerprint,
        String targetFingerprint,
        boolean allowsInPlaceExecution,
        boolean allowsRebuildExecution,
        List<PhysicalTableChangeExecutionOptionResponse> executionOptions,
        List<PhysicalTableChangeOperationResponse> operations,
        List<PhysicalTableChangeCheckResponse> checks,
        List<PhysicalTableChangeReasonResponse> reasons
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
