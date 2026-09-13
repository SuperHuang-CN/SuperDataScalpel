package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "一次非 LOCAL_SQL 任务运行登记的 result 与 log 制品摘要。两个字段始终返回元数据对象；对象尚未生成或存储元数据读取失败时由各自 availability 表达。")

public record TaskRunArtifactsResponse(
        @Schema(description = "任务运行 UUID。")
        UUID runId,
        @Schema(description = "result.json 制品元数据；未登记或对象不存在时仍返回 availability=NOT_GENERATED。")
        TaskRunArtifactMetadataResponse result,
        @Schema(description = "console.log 制品元数据；未登记或对象不存在时仍返回 availability=NOT_GENERATED。")
        TaskRunArtifactMetadataResponse log
) {
}
