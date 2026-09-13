package cn.superhuang.data.scalpel.business.dsh.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(description = "上传当前会话附件；不接收文件路径、远程 URL 或原生 DSH 文件引用")
public record UploadDshAttachmentRequest(
        @NotNull @Schema(description = "客户端生成的上传 UUID；网络中断后可用相同标识和内容重试") UUID clientAttachmentId,
        @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\\\/\\x00-\\x1F\\x7F]+")
        @Schema(description = "文件显示名称，最多 200 字符；不得包含路径或控制字符。支持 xlsx、xls、csv、tsv、txt、md、json、yaml、yml、png、jpg、jpeg、webp、gif") String name,
        @NotNull @Size(max = 6990508)
        @Schema(description = "原始文件的标准 Base64 编码，不带 data URL 前缀；解码后最多 5 MiB") String data) {}
