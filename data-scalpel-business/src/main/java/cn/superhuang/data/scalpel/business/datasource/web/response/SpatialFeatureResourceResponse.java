package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialFeatureResource;
import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "已登记的 ArcGIS 图层或 WFS Feature Type 空间要素资源")
public record SpatialFeatureResourceResponse(
        @Schema(description = "空间资源 UUID") UUID id,
        @Schema(description = "所属空间服务数据源 UUID") UUID dataSourceId,
        @Schema(description = "数据源内唯一资源编码") String code,
        @Schema(description = "资源显示名称") String name,
        @Schema(description = "远端空间服务协议：ARCGIS_REST 或 WFS") SpatialServiceProtocol protocol,
        @Schema(description = "远端 ArcGIS 图层或 WFS Feature Type 的稳定标识") String remoteIdentifier,
        @Schema(description = "是否允许任务引用并读取该资源") boolean enabled,
        @Schema(description = "远端资源说明名称；ArcGIS 为图层名称，WFS 当前为 FeatureType 远端标识") String serviceTitle,
        @Schema(description = "识别到的几何字段名称；远端资源没有几何字段或无法识别时为空") String geometryFieldName,
        @Schema(description = "资源输出坐标系的 EPSG 编码；无法识别时为空") Integer epsgCode,
        @Schema(description = "远端对象唯一标识字段；服务未声明时为空") String objectIdFieldName,
        @Schema(description = "WFS 协议版本；ArcGIS 资源为空") String wfsVersion,
        @Schema(description = "读取要素时使用的远端输出格式；ArcGIS 为 ESRI_JSON，WFS 当前为 AUTO") String outputFormat,
        @Schema(description = "坐标轴顺序提示；ArcGIS 当前为 XY，WFS 当前为 AUTO，由运行时结合版本和坐标系处理") String axisOrder,
        @Schema(description = "资源属性和几何字段的平台 Schema") List<CanvasColumnSchema> columns,
        @Schema(description = "资源创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "资源最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static SpatialFeatureResourceResponse from(SpatialFeatureResource resource, SpatialFeatureDefinition definition) {
        return new SpatialFeatureResourceResponse(
                resource.getId(), resource.getDataSourceId(), resource.getCode(), resource.getName(),
                resource.getProtocol(), resource.getRemoteIdentifier(), resource.isEnabled(),
                definition.serviceTitle(), definition.geometryFieldName(), definition.epsgCode(),
                definition.objectIdFieldName(), definition.wfsVersion(), definition.outputFormat(),
                definition.axisOrder(), definition.columns(), resource.getCreatedAt(), resource.getUpdatedAt()
        );
    }
}
