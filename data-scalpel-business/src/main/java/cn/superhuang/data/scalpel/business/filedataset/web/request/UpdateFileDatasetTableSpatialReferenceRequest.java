package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "确认 GDB 或 Shapefile 逻辑表的 EPSG 坐标参考系；只在文件元数据无法识别 EPSG 时作为回退，不能覆盖文件中可识别且不同的 EPSG。")

public record UpdateFileDatasetTableSpatialReferenceRequest(
        @Schema(description = "坐标参考系编码机构；当前只接受 EPSG，不区分大小写。")
        @NotBlank @Pattern(regexp = "(?i)EPSG") String authority,
        @Schema(description = "EPSG 数字编码，例如 4326 或 3857。")
        @Min(1) int code
) {
}
