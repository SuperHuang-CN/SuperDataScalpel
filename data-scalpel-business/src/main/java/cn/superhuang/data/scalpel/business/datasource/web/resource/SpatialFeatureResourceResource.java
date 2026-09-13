package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureResourceService;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialCatalogEntryResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialFeatureResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialFeaturePreviewResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/data-sources/{dataSourceId}/spatial-resources")
@Tag(name = "空间要素资源")
public class SpatialFeatureResourceResource {

    private final SpatialFeatureResourceService service;

    public SpatialFeatureResourceResource(SpatialFeatureResourceService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "发现 ArcGIS 图层或 WFS FeatureType")
    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "发现 ArcGIS 图层或 WFS FeatureType", description = "连接已启用的 ArcGIS REST 或 WFS 数据源并读取远端实时目录，不保存资源。ArcGIS 返回文件夹、FeatureServer/MapServer、图层和属性表；parent 可进入同源子路径。WFS 忽略 parent 并通过 GetCapabilities 返回全部 FeatureType。结果不分页、不排序且没有数量上限，单次响应正文最多读取 10 Mi 个字符。")
    public List<SpatialCatalogEntryResponse> discover(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "ArcGIS 可选父目录、服务相对路径或同源绝对 URL；省略时从数据源基地址发现。WFS 当前忽略此参数") @RequestParam(required = false) String parent
    ) {
        return service.discover(dataSourceId, parent);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询已登记的空间要素资源")
    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询已登记的空间要素资源", description = "读取指定数据源下已登记的全部空间要素资源及其 Schema 快照，按资源名称升序返回；不分页、不访问远端服务，数据源停用时仍可查看。")
    public List<SpatialFeatureResourceResponse> list(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId
    ) {
        return service.list(dataSourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询空间要素资源详情")
    @GetMapping("/{resourceId}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询空间要素资源详情", description = "读取一个已登记空间要素资源的定位信息、能力和字段 Schema 快照，不访问远端服务。")
    public SpatialFeatureResourceResponse get(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "空间要素资源 UUID") @PathVariable UUID resourceId
    ) {
        return service.get(dataSourceId, resourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "登记远程空间要素资源")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "登记远程空间要素资源", description = "真实调用 ArcGIS 图层描述，或 WFS GetCapabilities 与 DescribeFeatureType，解析成功后在短事务中登记为默认启用的资源并保存字段 Schema 快照；不复制要素数据。当前即使数据源处于停用状态也会尝试远端读取。远端调用成功而管理库保存失败时，不会在远端留下资源。")
    public SpatialFeatureResourceResponse create(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Valid @RequestBody CreateSpatialFeatureResourceRequest request
    ) {
        return service.create(dataSourceId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改空间要素资源")
    @PostMapping("/{resourceId}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改空间要素资源", description = "只修改已登记资源的显示名称和启停状态；远端标识、输出 EPSG 和字段 Schema 快照均保持不变，也不会访问远端服务。")
    public SpatialFeatureResourceResponse update(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "空间要素资源 UUID") @PathVariable UUID resourceId,
            @Valid @RequestBody UpdateSpatialFeatureResourceRequest request
    ) {
        return service.update(dataSourceId, resourceId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "刷新远程空间要素资源 Schema")
    @PostMapping("/{resourceId}/actions/refresh-schema")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "刷新远程空间要素资源 Schema", description = "使用已保存的远端标识和输出 EPSG 重新读取远端元数据；远端解析成功后在短事务中整体替换本地 Schema 快照，不修改远端服务或要素数据。资源名称和启停状态保持不变；当前即使数据源或资源停用也可刷新。")
    public SpatialFeatureResourceResponse refresh(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "空间要素资源 UUID") @PathVariable UUID resourceId
    ) {
        return service.refresh(dataSourceId, resourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览空间要素属性数据")
    @PostMapping("/{resourceId}/actions/query-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览空间要素属性数据", description = "要求数据源和资源均已启用，向远端请求 limit+1 个要素以判断截断，并最多返回 500 行属性样例。ArcGIS 使用 where=1=1、outFields=*、returnGeometry=false；WFS 使用 GetFeature 和 application/json。响应不返回几何值，不按本地 Schema 转换属性值，也不保证稳定排序；单次远端响应最多读取 10 Mi 个字符。")
    public SpatialFeaturePreviewResponse preview(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "空间要素资源 UUID") @PathVariable UUID resourceId,
            @Parameter(description = "最多返回的要素属性行数，范围 1 到 500") @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit
    ) {
        return service.preview(dataSourceId, resourceId, limit);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除空间要素资源")
    @PostMapping("/{resourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除空间要素资源", description = "立即删除管理库中的空间要素资源登记，不删除远端图层或 FeatureType。当前不会预检已有任务定义中的资源 UUID 引用；删除后这些任务在重新校验或运行时会因资源不存在而失败。")
    public void delete(
            @Parameter(description = "ArcGIS REST 或 WFS 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "空间要素资源 UUID") @PathVariable UUID resourceId
    ) {
        service.delete(dataSourceId, resourceId);
    }
}
