package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Read-only compilation of the current draft; does not save or apply a style. */
@Schema(description = "只读校验结构化制图样式并编译为 GeoServer 可用的 SLD XML。")
public record QuerySpatialStyleSldRequest(
        @Schema(description = "待转换为 SLD 的完整结构化制图样式；只生成结果，不保存到数据服务。")
        @NotNull @Valid SpatialStyleDocument styleDocument
) {
}
