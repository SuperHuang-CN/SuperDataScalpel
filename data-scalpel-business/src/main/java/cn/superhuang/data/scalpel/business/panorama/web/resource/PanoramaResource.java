package cn.superhuang.data.scalpel.business.panorama.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.panorama.service.PanoramaService;
import cn.superhuang.data.scalpel.business.panorama.web.request.*;
import cn.superhuang.data.scalpel.business.panorama.web.response.*;
import cn.superhuang.data.scalpel.business.system.configuration.domain.PanoramaMapConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
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
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询列表")
    @GetMapping @PreAuthorize("hasAuthority('panorama.view')")
    public PageResponse<PanoramaResponse> search(@ModelAttribute SearchRequest request,
            @RequestParam(required = false) List<UUID> directoryIds, @RequestParam(defaultValue = "false") boolean uncategorized) {
        return service.search(request, directoryIds, uncategorized);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查看详情")
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('panorama.view')")
    public PanoramaResponse get(@PathVariable UUID id) { return service.get(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：创建")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('panorama.create')")
    public PanoramaResponse create(@RequestPart MultipartFile file, @Valid @ModelAttribute UploadPanoramaRequest request) {
        return service.upload(file, request.clientRequestId(), request.directoryId(), null, null);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：替换文件")
    @PostMapping(path = "/{id}/actions/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('panorama.update')")
    public PanoramaResponse replace(@PathVariable UUID id, @RequestPart MultipartFile file, @Valid @ModelAttribute ReplacePanoramaRequest request) {
        return service.upload(file, request.clientRequestId(), null, id, request.expectedContentVersion());
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：修改")
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('panorama.update')")
    public PanoramaResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePanoramaRequest request) { return service.update(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "全景影像：重试执行")
    @PostMapping("/{id}/actions/retry-processing") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('panorama.update')")
    public PanoramaResponse retry(@PathVariable UUID id, @Valid @RequestBody PanoramaCandidateRequest request) { return service.retry(id, request.candidateId()); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：放弃处理")
    @PostMapping("/{id}/actions/discard-replacement") @PreAuthorize("hasAuthority('panorama.update')")
    public PanoramaResponse discard(@PathVariable UUID id, @Valid @RequestBody PanoramaCandidateRequest request) { return service.discard(id, request.candidateId()); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全景影像：删除")
    @PostMapping("/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('panorama.delete')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询地图配置")
    @GetMapping("/map-config") @PreAuthorize("hasAuthority('panorama.view')")
    public PanoramaMapConfiguration mapConfig() { return PanoramaMapConfiguration.parse(settings.requireValue("panorama.map")); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：查询地图点位")
    @GetMapping("/map-points") @PreAuthorize("hasAuthority('panorama.view')")
    public PanoramaMapPointsResponse points(@ModelAttribute SearchRequest request,
            @RequestParam(required = false) List<UUID> directoryIds, @RequestParam(defaultValue = "false") boolean uncategorized,
            @RequestParam(required = false) Double west, @RequestParam(required = false) Double south,
            @RequestParam(required = false) Double east, @RequestParam(required = false) Double north) {
        return service.mapPoints(request, directoryIds, uncategorized, west, south, east, north);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：读取缩略图")
    @GetMapping("/{id}/thumbnail") @PreAuthorize("hasAuthority('panorama.view')")
    public ResponseEntity<StreamingResponseBody> thumbnail(@PathVariable UUID id, @RequestParam long version) { return binary(id, version, "thumbnail", false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：预览")
    @GetMapping("/{id}/preview") @PreAuthorize("hasAuthority('panorama.view')")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable UUID id, @RequestParam long version) { return binary(id, version, "preview", false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "全景影像：下载内容")
    @GetMapping("/{id}/content") @PreAuthorize("hasAuthority('panorama.view')")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id, @RequestParam long version) { return binary(id, version, "original", true); }
    private ResponseEntity<StreamingResponseBody> binary(UUID id, long version, String variant, boolean download) {
        var binary = service.open(id, version, variant);
        StreamingResponseBody body = output -> { try (var content = binary.content()) { content.inputStream().transferTo(output); } };
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(binary.content().contentLength())
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(binary.filename(), StandardCharsets.UTF_8).build().toString()).body(body);
    }
}
