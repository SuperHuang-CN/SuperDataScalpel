package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetSpatialPreviewService;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelSpatialPreviewResponse;
import cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewService;
import cn.superhuang.data.scalpel.business.spatialpreview.web.request.PrepareSpatialPreviewRequest;
import cn.superhuang.data.scalpel.business.spatialpreview.web.response.SpatialPreviewStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/file-datasets/{datasetId}/tables/{tableId}")
@PreAuthorize("hasAuthority('filedataset.view')")
@Tag(name="文件数据集空间预览")
public class FileDatasetSpatialPreviewResource {
    private final FileDatasetSpatialPreviewService sources;
    private final SpatialPreviewService previews;
    public FileDatasetSpatialPreviewResource(FileDatasetSpatialPreviewService sources,SpatialPreviewService previews) {
        this.sources=sources;this.previews=previews;
    }
    @GetMapping("/spatial-preview")
    @Operation(summary="查询文件逻辑表空间预览能力",description="返回空间字段与来源 CRS，不读取文件正文；非空间表返回 supported=false。")
    public DataModelSpatialPreviewResponse inspect(
            @Parameter(description="文件数据集 UUID") @PathVariable UUID datasetId,
            @Parameter(description="该数据集中的逻辑表 UUID") @PathVariable UUID tableId) {
        return sources.inspect(datasetId,tableId);
    }
    @GetMapping("/spatial-preview/status")
    @Operation(summary="查询文件地图准备状态",description="只检查元数据和代次，不读取原文件；来源变化、装载或过期时撤销旧地图。")
    public SpatialPreviewStatusResponse status(
            @Parameter(description="文件数据集 UUID") @PathVariable UUID datasetId,
            @Parameter(description="逻辑表 UUID") @PathVariable UUID tableId,
            @Parameter(description="Geometry 字段名称") @RequestParam String geometryField) {
        return previews.status(sources.source(datasetId,tableId,geometryField));
    }
    @PostMapping("/actions/prepare-spatial-preview")
    @Operation(summary="准备或重新加载文件地图",description="异步完整读取逻辑表当前来源，生成副本和概览；202 返回状态，同来源准备或保存期间合并。完整概览可浏览时为 OVERVIEW_READY，副本保存成功后同代次变为 READY；副本保存失败仍保留完整概览。超过 120 秒/100 万条/2 GiB 不发布部分结果；繁忙返回 429。原文件不变。")
    public ResponseEntity<SpatialPreviewStatusResponse> prepare(
            @Parameter(description="文件数据集 UUID") @PathVariable UUID datasetId,
            @Parameter(description="逻辑表 UUID") @PathVariable UUID tableId,
            @Valid @RequestBody PrepareSpatialPreviewRequest request) {
        return ResponseEntity.accepted().body(previews.prepare(sources.source(datasetId,tableId,request.geometryField()),request.force()));
    }
    @GetMapping(value="/spatial-preview/map",produces=MediaType.IMAGE_PNG_VALUE)
    @Operation(summary="读取文件地图图片",description="OVERVIEW_READY/READY 可访问完整概览和本地细节，不重新解析原文件。其他节点在副本保存完成前返回概览，X-Spatial-Fallback=SHARING；保存完成后可恢复副本。实际范围由 X-Spatial-Bounds 返回；复杂视口返回同代次概览和 X-Spatial-Degraded=true。未就绪、过期或撤销的代次返回 409。")
    public ResponseEntity<byte[]> image(
            @Parameter(description="文件数据集 UUID") @PathVariable UUID datasetId,
            @Parameter(description="逻辑表 UUID") @PathVariable UUID tableId,
            @Parameter(description="Geometry 字段名称") @RequestParam String geometryField,
            @Parameter(description="当前代次；省略时使用服务端当前有效代次") @RequestParam(required=false) UUID generation,
            @Parameter(description="EPSG:3857 范围 minX,minY,maxX,maxY") @RequestParam String bbox,
            @Parameter(description="图片宽度，256–1600 像素") @RequestParam int width,
            @Parameter(description="图片高度，256–1200 像素") @RequestParam int height,
            @Parameter(description="只读取完整概览，默认 false") @RequestParam(defaultValue="false") boolean overview,
            @Parameter(description="页面随机 UUID；可选，用于终止同页面的过时视口计算") @RequestParam(required=false) UUID clientId,
            @Parameter(description="页面内递增视口序号，默认 0；较旧请求返回 409") @RequestParam(defaultValue="0") long sequence) {
        return previews.image(sources.source(datasetId,tableId,geometryField),generation,bbox,width,height,overview,clientId,sequence).response();
    }
}
