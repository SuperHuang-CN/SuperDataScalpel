package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "将当前空间样式解析或生成后的 OGC SLD 文档。")

public record SpatialStyleSldResponse(
        @Schema(description = "完整 SLD 样式 XML 文本。")
        String sldText
) {
}
