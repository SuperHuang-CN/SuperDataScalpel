package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "文件准备或表来源校验作业的队列、重试、租约和执行结果。")

public record FileDatasetParseJobResponse(
        @Schema(description = "后台解析作业 UUID。")
        UUID id,
        @Schema(description = "作业类型：FILE_PREPARATION 负责展开和规范化上传文件，TABLE_SOURCE_VALIDATE 负责校验并加载一个表来源。")
        FileDatasetParseJobType type,
        @Schema(description = "作业提交时所属的文件数据集 UUID，作业记录中始终有值；数据集之后可能已被删除。")
        UUID fileDatasetId,
        @Schema(description = "排队时保存的文件数据集名称快照；原数据集已删除时仍可用于诊断。")
        String fileDatasetName,
        @Schema(description = "本作业读取或准备的上传文件 UUID，作业记录中始终有值；文件之后可能已被删除。")
        UUID sourceFileId,
        @Schema(description = "排队时保存的来源文件名快照。")
        String sourceFileName,
        @Schema(description = "本作业最终关联的逻辑表 UUID。TABLE_SOURCE_VALIDATE 始终有值；为已有表上传文件而创建的 FILE_PREPARATION 也有值，初始文件发现作业为空。")
        UUID fileDatasetTableId,
        @Schema(description = "排队时保存的目标逻辑表名称快照；未绑定具体逻辑表的初始文件准备作业为空。")
        String tableName,
        @Schema(description = "表来源加载方式；未绑定具体逻辑表的初始 FILE_PREPARATION 作业为空。FILE_PREPARATION 绑定表时，该值会传递给后续 TABLE_SOURCE_VALIDATE 作业。")
        FileDatasetTableSourceLoadMode loadMode,
        @Schema(description = "REPLACE_SOURCE 模式要替换的既有表来源 UUID；其他加载模式为空。")
        UUID targetSourceId,
        @Schema(description = "要从文件中加载的工作表、图层或对象条目显示名称；未预先指定条目的文件准备作业为空。")
        String sourceName,
        @Schema(description = "来源在该文件内的定位键，例如工作表名、图层名或对象条目路径；未预先指定条目的文件准备作业为空，只在对应文件内容不变时稳定。")
        String sourceKey,
        @Schema(description = "队列状态。FAILED 既可能表示不可重试错误，也可能表示已耗尽最大尝试次数；RUNNING 的租约可能已过期但尚未被调度器回收。")
        FileDatasetParseJobStatus status,
        @Schema(description = "该作业已经开始执行的次数，包括失败后重试的尝试。")
        int attemptCount,
        @Schema(description = "解析作业允许的最大尝试次数。")
        int maxAttempts,
        @Schema(description = "作业下一次允许被领取的时间，ISO-8601 UTC 时间戳；首次排队等于 queuedAt，重试时为退避结束时间。终态仍保留最后值。")
        Instant availableAt,
        @Schema(description = "作业首次入队时间，ISO-8601 UTC 时间戳，始终有值；重试不会改变。")
        Instant queuedAt,
        @Schema(description = "最近一次尝试被 Worker 领取的时间，ISO-8601 UTC 时间戳；从未领取时为空，重试领取会覆盖旧值。QUEUED 重试期间可能仍保留上次开始时间。")
        Instant startedAt,
        @Schema(description = "作业进入 SUCCEEDED、FAILED 或 CANCELLED 的时间，ISO-8601 UTC 时间戳；QUEUED 或 RUNNING 时为空。")
        Instant completedAt,
        @Schema(description = "当前领取该作业的后台工作实例标识；作业未运行或租约已释放时为空。")
        String leaseOwner,
        @Schema(description = "当前 Worker 租约的到期时间，ISO-8601 UTC 时间戳；仅 RUNNING 时有值。到期只表示可被恢复流程接管，状态可能暂时仍为 RUNNING。")
        Instant leaseExpiresAt,
        @Schema(description = "最近一次执行尝试的最后心跳时间，ISO-8601 UTC 时间戳；从未领取时为空。作业离开 RUNNING 后仍保留该历史时间。")
        Instant lastHeartbeatAt,
        @Schema(description = "经安全处理的最近错误、重试原因或取消原因，最长 2000 字符；成功后为空。QUEUED 且 attemptCount>0 时通常说明上次尝试失败并正在等待重试。")
        String errorMessage,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static FileDatasetParseJobResponse from(FileDatasetParseJob job) {
        return new FileDatasetParseJobResponse(
                job.getId(), job.getType(), job.getFileDatasetId(), job.getDatasetNameSnapshot(),
                job.getSourceFileId(), job.getFileNameSnapshot(), job.getFileDatasetTableId(),
                job.getTableNameSnapshot(), job.getLoadMode(), job.getTargetSourceId(),
                job.getSourceName(), job.getSourceKey(), job.getStatus(), job.getAttemptCount(),
                job.getMaxAttempts(), job.getAvailableAt(), job.getQueuedAt(), job.getStartedAt(),
                job.getCompletedAt(), job.getLeaseOwner(), job.getLeaseExpiresAt(),
                job.getLastHeartbeatAt(), job.getErrorMessage(), job.getCreatedAt(), job.getUpdatedAt()
        );
    }
}
