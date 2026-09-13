package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "新增、文件导入或批量删除数据的执行结果和人工核对标记。")

public record DataEntryOperationLogResponse(
        @Schema(description = "填报操作日志 UUID。")
        UUID id,
        @Schema(description = "发起操作时使用的填报表单 UUID；关联表单已删除或日志来自兼容数据时可能为空。")
        UUID formId,
        @Schema(description = "操作实际作用的目标模型 UUID。")
        UUID modelId,
        @Schema(description = "发起本次操作时固化的目标模型字段结构版本；模型后续变化不会改写该历史值，兼容旧日志时可能为空。")
        Integer modelSchemaVersion,
        @Schema(description = "操作类型：INSERT 单条新增，IMPORT 文件批量导入，DELETE 删除已有记录。")
        DataEntryOperationType operationType,
        @Schema(description = "操作状态：PROCESSING 执行中，SUCCEEDED 全部成功，PARTIALLY_SUCCEEDED 部分成功，FAILED 已确认失败。")
        DataEntryOperationStatus status,
        @Schema(description = "执行操作的系统用户名。")
        String operatorUsername,
        @Schema(description = "客户端要求处理的记录数：INSERT 通常为 1，IMPORT 为文件有效数据行数，DELETE 为业务主键数量。")
        int requestedCount,
        @Schema(description = "已确认成功写入或删除的记录数；仍在处理或底层无法确认时为空。")
        Integer affectedCount,
        @Schema(description = "详情查询返回的操作摘要快照，不包含凭据和完整行数据；列表查询为减小响应体而返回空。")
        String payloadSnapshot,
        @Schema(description = "操作被确认全部成功、部分成功或失败的时间，ISO-8601 UTC 时间戳；仍为 PROCESSING 时为空。")
        Instant completedAt,
        @Schema(description = "稳定错误码；没有错误时为空。")
        String errorCode,
        @Schema(description = "经安全处理的错误说明；没有错误时为空。")
        String errorMessage,
        @Schema(description = "是否需要人工核对目标数据库。PARTIALLY_SUCCEEDED 时为 true；PROCESSING 超过 5 分钟仍未收敛时也为 true，因为目标库可能已提交而管理库结果未能更新。系统不会自动重放。")
        boolean manualVerificationRequired,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static DataEntryOperationLogResponse summary(DataEntryOperationLog log) {
        return from(log, false);
    }

    public static DataEntryOperationLogResponse detail(DataEntryOperationLog log) {
        return from(log, true);
    }

    private static DataEntryOperationLogResponse from(DataEntryOperationLog log, boolean includePayload) {
        boolean stale = log.getStatus() == DataEntryOperationStatus.PROCESSING
                && log.getCreatedAt() != null
                && Duration.between(log.getCreatedAt(), Instant.now()).toMinutes() >= 5;
        boolean manualVerification = stale || log.getStatus() == DataEntryOperationStatus.PARTIALLY_SUCCEEDED;
        return new DataEntryOperationLogResponse(
                log.getId(), log.getFormId(), log.getModelId(), log.getModelSchemaVersion(), log.getOperationType(),
                log.getStatus(), log.getOperatorUsername(), log.getRequestedCount(), log.getAffectedCount(),
                includePayload ? log.getPayloadSnapshot() : null, log.getCompletedAt(), log.getErrorCode(),
                log.getErrorMessage(), manualVerification, log.getCreatedAt(), log.getUpdatedAt()
        );
    }
}
