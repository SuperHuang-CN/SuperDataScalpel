package cn.superhuang.data.scalpel.business.spatialpreview.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description="空间预览准备命令；只写预览缓存，不修改源表、原始文件、索引或底图配置")
public record PrepareSpatialPreviewRequest(
        @NotBlank @Schema(description="需要预览的 Geometry 字段名称") String geometryField,
        @Schema(description="是否强制重读来源；默认 false。空闲时 true 撤销旧代次；正在读取、生成概览或保存共享副本时合并请求，保持当前代次") boolean force
) { }
