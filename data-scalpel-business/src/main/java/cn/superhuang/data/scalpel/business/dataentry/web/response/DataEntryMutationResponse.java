package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationStatus;

import java.util.UUID;

@Schema(description = "数据填报新增、导入或删除操作的执行结果。")

public record DataEntryMutationResponse(
        @Schema(description = "本次新增、导入或删除对应的操作日志 UUID；可用于查询实际执行详情和逐记录变更。")
        UUID operationLogId,
        @Schema(description = "客户端请求处理的记录数。")
        int requestedCount,
        @Schema(description = "实际受影响的记录数。")
        int affectedCount,
        @Schema(description = "执行结果：SUCCEEDED 全部成功，PARTIALLY_SUCCEEDED 部分生效并需结合计数和告警判断。")
        DataEntryOperationStatus status,
        @Schema(description = "结果是否因外部执行不确定而需要人工核对。")
        boolean manualVerificationRequired,
        @Schema(description = "非阻断风险说明；没有时为空。")
        String warningMessage
) {

    public static DataEntryMutationResponse succeeded(UUID operationLogId, int requestedCount, int affectedCount) {
        return new DataEntryMutationResponse(
                operationLogId, requestedCount, affectedCount, DataEntryOperationStatus.SUCCEEDED, false, null
        );
    }

    public static DataEntryMutationResponse partiallySucceeded(
            UUID operationLogId,
            int requestedCount,
            int affectedCount,
            boolean manualVerificationRequired,
            String warningMessage
    ) {
        return new DataEntryMutationResponse(
                operationLogId,
                requestedCount,
                affectedCount,
                DataEntryOperationStatus.PARTIALLY_SUCCEEDED,
                manualVerificationRequired,
                warningMessage
        );
    }
}
