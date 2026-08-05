package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureResourceService;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateSpatialFeatureResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialCatalogEntryResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialFeatureResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "发现 ArcGIS 图层或 WFS FeatureType")
    public List<SpatialCatalogEntryResponse> discover(
            @PathVariable UUID dataSourceId,
            @RequestParam(required = false) String parent
    ) {
        return service.discover(dataSourceId, parent);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询已登记的空间要素资源")
    public List<SpatialFeatureResourceResponse> list(@PathVariable UUID dataSourceId) {
        return service.list(dataSourceId);
    }

    @GetMapping("/{resourceId}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询空间要素资源详情")
    public SpatialFeatureResourceResponse get(@PathVariable UUID dataSourceId, @PathVariable UUID resourceId) {
        return service.get(dataSourceId, resourceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "登记远程空间要素资源")
    public SpatialFeatureResourceResponse create(
            @PathVariable UUID dataSourceId, @Valid @RequestBody CreateSpatialFeatureResourceRequest request
    ) {
        return service.create(dataSourceId, request);
    }

    @PostMapping("/{resourceId}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改空间要素资源")
    public SpatialFeatureResourceResponse update(
            @PathVariable UUID dataSourceId,
            @PathVariable UUID resourceId,
            @Valid @RequestBody UpdateSpatialFeatureResourceRequest request
    ) {
        return service.update(dataSourceId, resourceId, request);
    }

    @PostMapping("/{resourceId}/actions/refresh-schema")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "刷新远程空间要素资源 Schema")
    public SpatialFeatureResourceResponse refresh(@PathVariable UUID dataSourceId, @PathVariable UUID resourceId) {
        return service.refresh(dataSourceId, resourceId);
    }

    @PostMapping("/{resourceId}/actions/query-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览空间要素属性数据")
    public SpatialFeaturePreviewResponse preview(
            @PathVariable UUID dataSourceId,
            @PathVariable UUID resourceId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit
    ) {
        return service.preview(dataSourceId, resourceId, limit);
    }

    @PostMapping("/{resourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除空间要素资源")
    public void delete(@PathVariable UUID dataSourceId, @PathVariable UUID resourceId) {
        service.delete(dataSourceId, resourceId);
    }
}
