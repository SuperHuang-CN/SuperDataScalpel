package cn.superhuang.data.scalpel.business.dsh.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "当前用户会话中的附件；上传完成后将 id 随聊天消息发送")
public record DshAttachmentResponse(
        @Schema(description = "附件 UUID，只能在上传时所属会话内引用或下载") UUID id,
        @Schema(description = "原始文件显示名称") String name,
        @Schema(description = "已确认的文件媒体类型") String mediaType,
        @Schema(description = "原始文件大小，单位字节，最多 5242880") long bytes,
        @Schema(description = "附件种类：image 图片，file 可解析的文本或 Excel", allowableValues = {"image", "file"}) String kind,
        @Schema(description = "供模型读取的图片宽度，单位像素；非图片为空") Integer width,
        @Schema(description = "供模型读取的图片高度，单位像素；非图片为空") Integer height) {}
