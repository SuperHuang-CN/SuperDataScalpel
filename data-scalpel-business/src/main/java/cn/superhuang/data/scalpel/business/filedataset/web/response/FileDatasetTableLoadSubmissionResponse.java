package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "文件表加载请求受理结果，包含排队作业及当前文件、表状态。")

public record FileDatasetTableLoadSubmissionResponse(
        @Schema(description = "已受理的表来源加载作业 UUID；调用方据此查询异步执行状态。")
        UUID jobId,
        @Schema(description = "提交加载后来源文件的最新状态快照。")
        FileDatasetFileResponse file,
        @Schema(description = "提交加载后目标文件数据集表的最新状态快照。")
        FileDatasetTableResponse table
) {
}
