package cn.superhuang.data.scalpel.business.panorama.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.panorama.service.PanoramaService;
import cn.superhuang.data.scalpel.business.panorama.web.request.*;
import cn.superhuang.data.scalpel.business.panorama.web.response.*;
import cn.superhuang.data.scalpel.business.system.configuration.domain.PanoramaMapConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "全景影像")
@RestController
@RequestMapping("/api/v1/panoramas")
public class PanoramaResource {
    private final PanoramaService service;
    private final SystemConfigurationService settings;
    public PanoramaResource(PanoramaService service, SystemConfigurationService settings) { this.service = service; this.settings = settings; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询列表",
            keywords = {"全景影像", "全景照片", "目录", "检索"},
            relatedOperations = {"GET /api/v1/panoramas/{id}", "GET /api/v1/panoramas/map-points"})
    @GetMapping @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "查询全景影像", description = "按通用 Search DSL 和目录范围分页查询全景资源。directoryIds 与 uncategorized 不能同时使用；该接口返回资源及当前、候选内容状态，不读取图片二进制。")
    public PageResponse<PanoramaResponse> search(@ParameterObject @ModelAttribute SearchRequest request,
            @Parameter(description = "限定一个或多个目录 UUID；省略时不按目录过滤。") @RequestParam(required = false) List<UUID> directoryIds,
            @Parameter(description = "是否只查询未归入任何目录的全景；为 true 时 directoryIds 必须为空。") @RequestParam(defaultValue = "false") boolean uncategorized) {
        return service.search(request, directoryIds, uncategorized);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查看详情",
            keywords = {"全景影像", "详情", "处理状态", "候选内容"},
            relatedOperations = {"GET /api/v1/panoramas", "GET /api/v1/panoramas/{id}/preview"})
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "查看全景影像详情", description = "返回全景的治理信息、有效时间和位置，以及当前成品、候选替换内容和异步处理状态；不返回图片二进制或对象存储地址。")
    public PanoramaResponse get(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id) { return service.get(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：创建")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('panorama.create')")
    @Operation(summary = "上传并创建全景影像", description = "同步校验并保存最大 100 MiB、宽高严格为 2:1 且宽度不超过 32768 像素的完整 JPEG 球形成品，创建状态为 QUEUED 的资源后返回 201；预览图、缩略图和白名单元数据由单线程后台 Worker 异步生成。clientRequestId 可识别已成功接收的同一次上传。该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public PanoramaResponse create(@Parameter(description = "完整 JPG/JPEG 球形成品，最大 100 MiB；仅扩展名、JPEG 结构、尺寸、投影及裁剪范围在接收阶段校验，派生图生成失败会在资源状态中体现。") @RequestPart MultipartFile file, @Valid @ModelAttribute UploadPanoramaRequest request) {
        return service.upload(file, request.clientRequestId(), request.directoryId(), null, null);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：替换文件",
            prerequisites = "全景存在且 expectedContentVersion 等于当前内容版本；没有其他候选内容正在处理。")
    @PostMapping(path = "/{id}/actions/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('panorama.update')")
    @Operation(summary = "上传全景替换文件", description = "同步校验并保存新的完整 JPEG 候选后返回 202。QUEUED 或 PROCESSING 候选存在时拒绝；FAILED 候选可由新上传替代。旧成品在候选处理期间和处理失败后继续可用；成功后候选原子成为当前内容并递增 contentVersion。该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public PanoramaResponse replace(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Parameter(description = "新的完整 2:1 球形 JPEG 文件，最大 100 MiB。") @RequestPart MultipartFile file, @Valid @ModelAttribute ReplacePanoramaRequest request) {
        return service.upload(file, request.clientRequestId(), null, id, request.expectedContentVersion());
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：修改",
            keywords = {"全景影像", "名称", "目录", "拍摄时间", "经纬度"},
            prerequisites = "全景存在且 expectedContentVersion 等于当前内容版本。",
            relatedOperations = {"GET /api/v1/panoramas/{id}"})
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('panorama.update')")
    @Operation(summary = "修改全景影像资料", description = "整体修改名称、说明、目录，以及时间和位置的自动/人工取值方式。AUTO 会立即重新采用当前成品解析出的元数据，缺失时清空对应值；MANUAL 使用请求值并允许成对清空坐标。不会替换、重新处理图片或递增 contentVersion。")
    public PanoramaResponse update(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdatePanoramaRequest request) { return service.update(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "全景影像：重试处理",
            keywords = {"全景影像", "候选内容", "处理失败", "重试"},
            prerequisites = "指定 candidateId 是该全景当前处理失败的候选内容。",
            relatedOperations = {"GET /api/v1/panoramas/{id}", "POST /api/v1/panoramas/{id}/actions/discard-replacement"})
    @PostMapping("/{id}/actions/retry-processing") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('panorama.update')")
    @Operation(summary = "重试处理全景候选内容", description = "将处理失败的当前候选内容重新排队并返回 202；不会上传新文件，也不会在处理成功前替换当前可用成品。")
    public PanoramaResponse retry(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Valid @RequestBody PanoramaCandidateRequest request) { return service.retry(id, request.candidateId()); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：放弃候选替换",
            keywords = {"全景影像", "候选内容", "放弃替换"},
            prerequisites = "指定 candidateId 是该全景 processingStatus=FAILED 的当前候选内容，且已有可用 currentContent。",
            relatedOperations = {"GET /api/v1/panoramas/{id}"})
    @PostMapping("/{id}/actions/discard-replacement") @PreAuthorize("hasAuthority('panorama.update')")
    @Operation(summary = "放弃全景候选替换", description = "仅允许放弃处理失败的替换候选；移除候选、将其文件转入延迟清理并恢复 READY 状态，现有 currentContent 保持不变。首次上传失败且没有 currentContent 时拒绝放弃，应重试、重新上传或删除资源。")
    public PanoramaResponse discard(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Valid @RequestBody PanoramaCandidateRequest request) { return service.discard(id, request.candidateId()); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：删除",
            keywords = {"全景影像", "删除"},
            prerequisites = "全景不存在 processingStatus=PROCESSING 的候选；QUEUED 或 FAILED 候选可随资源一并删除。")
    @PostMapping("/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('panorama.delete')")
    @Operation(summary = "删除全景影像", description = "删除资源记录并将当前及候选文件转入延迟清理，返回 204。仅 processingStatus=PROCESSING 时拒绝；QUEUED 或 FAILED 候选可一并删除。当前实现不检查资产登记，相关资产会在后续同步时反映来源缺失。")
    public void delete(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id) { service.delete(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询地图配置",
            keywords = {"全景影像", "地图", "XYZ", "底图"})
    @GetMapping("/map-config") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "查询全景地图底图配置", description = "返回系统配置 panorama.map 中的浏览器端 XYZ 瓦片地址、纯文本署名和最大缩放级别；服务端不会请求该地址。")
    public PanoramaMapConfiguration mapConfig() { return PanoramaMapConfiguration.parse(settings.requireValue("panorama.map")); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询地图点位",
            keywords = {"全景影像", "地图", "经纬度", "范围检索"},
            relatedOperations = {"GET /api/v1/panoramas", "GET /api/v1/panoramas/{id}"})
    @GetMapping("/map-points") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "查询全景地图点位", description = "保留 SearchRequest.search 条件，并按目录和可选 WGS84 包围盒筛选已有当前成品及有效坐标的全景；忽略请求中的 page、size 和 sort，固定按 name、id 排序并最多返回 500 个点位。totalElements 是全部匹配数，超过上限时 truncated=true。四个边界参数必须同时提供或全部省略；west>east 表示跨越日期变更线。")
    public PanoramaMapPointsResponse points(@ParameterObject @ModelAttribute SearchRequest request,
            @Parameter(description = "限定一个或多个目录 UUID；省略时不按目录过滤。") @RequestParam(required = false) List<UUID> directoryIds,
            @Parameter(description = "是否只查询未归入任何目录的全景；为 true 时 directoryIds 必须为空。") @RequestParam(defaultValue = "false") boolean uncategorized,
            @Parameter(description = "包围盒西边界经度，范围 -180 到 180；可大于 east，此时查询跨越日期变更线的范围。") @RequestParam(required = false) Double west,
            @Parameter(description = "包围盒南边界纬度，范围 -90 到 90。") @RequestParam(required = false) Double south,
            @Parameter(description = "包围盒东边界经度，范围 -180 到 180；小于 west 时表示跨越日期变更线。") @RequestParam(required = false) Double east,
            @Parameter(description = "包围盒北边界纬度，范围 -90 到 90，且不得小于 south。") @RequestParam(required = false) Double north) {
        return service.mapPoints(request, directoryIds, uncategorized, west, south, east, north);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：读取缩略图")
    @GetMapping("/{id}/thumbnail") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "读取全景缩略图", description = "按内容版本返回 JPEG 缩略图流并禁止缓存；该二进制接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> thumbnail(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Parameter(description = "必须等于资源当前 contentVersion，避免读取已替换内容。") @RequestParam long version) { return binary(id, version, "thumbnail", false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：预览")
    @GetMapping("/{id}/preview") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "读取全景预览图", description = "按内容版本返回适合 360° 查看器的 JPEG 预览图流并禁止缓存；该二进制接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> preview(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Parameter(description = "必须等于资源当前 contentVersion，避免读取已替换内容。") @RequestParam long version) { return binary(id, version, "preview", false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：下载内容")
    @GetMapping("/{id}/content") @PreAuthorize("hasAuthority('panorama.view')")
    @Operation(summary = "下载全景原图", description = "按内容版本返回带附件文件名的原始 JPEG 文件流并禁止缓存；该二进制接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> content(@Parameter(description = "全景影像 UUID。") @PathVariable UUID id, @Parameter(description = "必须等于资源当前 contentVersion，避免下载已替换内容。") @RequestParam long version) { return binary(id, version, "original", true); }
    private ResponseEntity<StreamingResponseBody> binary(UUID id, long version, String variant, boolean download) {
        var binary = service.open(id, version, variant);
        StreamingResponseBody body = output -> { try (var content = binary.content()) { content.inputStream().transferTo(output); } };
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(binary.content().contentLength())
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(binary.filename(), StandardCharsets.UTF_8).build().toString()).body(body);
    }
}
