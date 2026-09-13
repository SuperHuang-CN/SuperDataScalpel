package cn.superhuang.data.scalpel.business.panorama.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(description = "上传全景替换文件时随 multipart 一同提交的幂等标识和图片内容版本前置条件。")
public record ReplacePanoramaRequest(
        @Schema(description = "客户端为本次文件上传生成的 UUID。请求已成功接收且对应内容仍保留时，重复提交返回同一全景当前状态；不能复用于其他资源或上传操作。")
        @NotNull UUID clientRequestId,
        @Schema(description = "从最新全景详情读取的 contentVersion；仅用于确认当前图片内容未被替换，不检测名称、目录、时间或位置等治理字段的并发修改。")
        @NotNull @Min(0) Long expectedContentVersion
) {}
