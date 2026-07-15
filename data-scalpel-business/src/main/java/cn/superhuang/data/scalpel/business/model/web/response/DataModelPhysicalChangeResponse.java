package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;

import java.time.Instant;
import java.util.UUID;

public record DataModelPhysicalChangeResponse(
        UUID id,
        UUID modelId,
        int baseSchemaVersion,
        int targetSchemaVersion,
        DataModelPhysicalChangeStatus status,
        PhysicalTableChangePlanResponse plan,
        TableChangeExecutionMode executionMode,
        Instant executionStartedAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataModelPhysicalChangeResponse from(DataModelPhysicalChange change, TableChangePlan plan) {
        return new DataModelPhysicalChangeResponse(
                change.getId(), change.getModelId(), change.getBaseSchemaVersion(), change.getTargetSchemaVersion(), change.getStatus(),
                PhysicalTableChangePlanResponse.from(plan), change.getExecutionMode(), change.getExecutionStartedAt(), change.getCompletedAt(),
                change.getErrorCode(), change.getErrorMessage(), change.getCreatedAt(), change.getUpdatedAt()
        );
    }
}
