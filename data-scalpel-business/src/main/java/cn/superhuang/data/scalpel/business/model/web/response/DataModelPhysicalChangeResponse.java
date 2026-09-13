package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "持久化的模型物理结构变更计划、冻结快照和执行状态")
public record DataModelPhysicalChangeResponse(
        @Schema(description = "变更计划 UUID") UUID id,
        @Schema(description = "所属模型 UUID") UUID modelId,
        @Schema(description = "生成计划时模型的 Schema 版本；当前版本变化后计划不可执行") int baseSchemaVersion,
        @Schema(description = "执行成功后模型应达到的 Schema 版本") int targetSchemaVersion,
        @Schema(description = "计划状态：PLANNED、APPLYING、SUCCEEDED、FAILED、PARTIAL、CANCELLED 或 SUPERSEDED") DataModelPhysicalChangeStatus status,
        @Schema(description = "生成时冻结的方言计划快照；不会按当前字段重新推导") PhysicalTableChangePlanResponse plan,
        @Schema(description = "实际选择的执行方式；尚未执行时为空") TableChangeExecutionMode executionMode,
        @Schema(description = "执行开始时间；尚未执行时为空") Instant executionStartedAt,
        @Schema(description = "执行完成或终止时间；仍在应用时为空") Instant completedAt,
        @Schema(description = "稳定失败或部分完成错误码；无错误时为空") String errorCode,
        @Schema(description = "失败说明；PARTIAL 表示物理操作可能已完成但管理库收尾或后置校验失败，必须人工核验") String errorMessage,
        @Schema(description = "计划创建时间") Instant createdAt,
        @Schema(description = "计划状态最后更新时间") Instant updatedAt
) {
    public static DataModelPhysicalChangeResponse from(DataModelPhysicalChange change, TableChangePlan plan) {
        return new DataModelPhysicalChangeResponse(
                change.getId(), change.getModelId(), change.getBaseSchemaVersion(), change.getTargetSchemaVersion(), change.getStatus(),
                PhysicalTableChangePlanResponse.from(plan), change.getExecutionMode(), change.getExecutionStartedAt(), change.getCompletedAt(),
                change.getErrorCode(), change.getErrorMessage(), change.getCreatedAt(), change.getUpdatedAt()
        );
    }
}
