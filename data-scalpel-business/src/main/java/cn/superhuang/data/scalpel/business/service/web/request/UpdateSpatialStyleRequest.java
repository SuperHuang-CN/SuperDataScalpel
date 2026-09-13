package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import jakarta.validation.Valid;

@Schema(description = "保存完整结构化制图样式，或重新激活此前上传并保留的 SLD；不会自动应用到已部署 GeoServer。")

public record UpdateSpatialStyleRequest(
        @Schema(description = "目标样式来源；省略时为 CARTOGRAPHY。CARTOGRAPHY 要求提交 styleDocument；UPLOADED_SLD 要求此前已经上传 SLD 且本次 styleDocument 必须为空；SIMPLE 会被拒绝。")
        SpatialStyleMode mode,
        @Schema(description = "CARTOGRAPHY 模式的完整结构化样式，序列化后最多 256 KiB；服务端校验几何类型、字段能力、颜色、尺寸和规则一致性。UPLOADED_SLD 模式必须为空。")
        @Valid SpatialStyleDocument styleDocument
) {
}
