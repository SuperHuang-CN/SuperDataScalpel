package cn.superhuang.data.scalpel.business.panorama.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@Schema(description = "一次全景上传形成的内容摘要。宽高、大小和摘要描述原始 JPEG；元数据仅在候选处理成功并成为当前内容后持久化。")
public record PanoramaContentResponse(
        @Schema(description = "内容 UUID；当前内容和候选内容各自拥有独立 ID")
        UUID id,
        @Schema(description = "上传时的原始文件名。")
        String originalFilename,
        @Schema(description = "上传原始 JPEG 的大小，单位字节；不包含预览图和缩略图。")
        long byteSize,
        @Schema(description = "原图宽度，单位像素。")
        int width,
        @Schema(description = "原图高度，单位像素。")
        int height,
        @Schema(description = "上传原始 JPEG 字节的 SHA-256 小写十六进制摘要。")
        String sha256,
        @Schema(description = "从原图解析并经过白名单过滤的时间、位置、设备和全景元数据；候选处于 QUEUED、PROCESSING 或 FAILED 时为空。")
        PanoramaMetadataResponse metadata
) {}
