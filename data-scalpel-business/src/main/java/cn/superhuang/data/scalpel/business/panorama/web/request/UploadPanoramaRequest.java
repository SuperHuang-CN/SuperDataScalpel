package cn.superhuang.data.scalpel.business.panorama.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
@Schema(description = "首次上传全景文件时随 multipart 一同提交的幂等标识和目录。")
public record UploadPanoramaRequest(
        @Schema(description = "客户端为本次文件上传生成的 UUID。请求已成功接收且对应内容仍保留时，重复提交返回同一资源当前状态；不能复用于其他文件、资源或操作。")
        @NotNull UUID clientRequestId,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId
) {}
