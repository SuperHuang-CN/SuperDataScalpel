package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "ArcGIS REST 或 WFS 服务发现目录中的空间图层条目")
public record SpatialCatalogEntryResponse(
        @Schema(description = "远端空间服务协议") SpatialServiceProtocol protocol,
        @Schema(description = "登记资源时使用的远端图层标识") String remoteIdentifier,
        @Schema(description = "远端图层技术名称") String name,
        @Schema(description = "远端图层显示标题") String title,
        @Schema(description = "远端对象种类，例如 ArcGIS Feature Layer 或 WFS Feature Type") String kind,
        @Schema(description = "远端服务是否声明该图层可查询") boolean queryable,
        @Schema(description = "远端图层声明的 EPSG 编码；无法识别时为空") Integer epsgCode
) {
}
