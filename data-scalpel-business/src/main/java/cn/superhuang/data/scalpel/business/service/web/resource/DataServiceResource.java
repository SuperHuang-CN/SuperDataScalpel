package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.lineage.service.DataServiceLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageFieldGraphResponse;
import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.DataServiceRelatedModelService;
import cn.superhuang.data.scalpel.business.service.StandardDataServiceModelCandidateService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServiceModelCandidateService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServicePreviewService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServiceStyleService;
import cn.superhuang.data.scalpel.business.service.SpatialStyleFieldProfileService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateSpatialStyleRequest;
import cn.superhuang.data.scalpel.business.service.web.request.QuerySpatialStyleSldRequest;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleSldResponse;
import cn.superhuang.data.scalpel.business.service.web.request.SpatialStyleFieldProfileRequest;
import cn.superhuang.data.scalpel.business.service.web.request.RenderSpatialStylePreviewRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SqlServiceTestRequest;
import cn.superhuang.data.scalpel.business.service.web.request.QueryDataServiceFieldLineageRequest;
import cn.superhuang.data.scalpel.business.service.web.request.PublishDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceDetailResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceSummaryResponse;
import cn.superhuang.data.scalpel.business.service.web.response.GatewayDataServicePublicationResponse;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestResponse;
import cn.superhuang.data.scalpel.business.service.web.response.StandardDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServicePreviewResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceStyleResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleFieldProfileResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "数据服务")
@RestController
@RequestMapping("/api/v1/data-services")
public class DataServiceResource {

    private final DataServiceManagementService service;
    private final StandardDataServiceModelCandidateService modelCandidateService;
    private final DataServiceRelatedModelService relatedModelService;
    private final SpatialDataServiceModelCandidateService spatialModelCandidateService;
    private final SpatialDataServicePreviewService spatialPreviewService;
    private final SpatialDataServiceStyleService spatialStyleService;
    private final SpatialStyleFieldProfileService spatialStyleFieldProfileService;
    private final DataServiceLineageQueryService lineageQueryService;

    public DataServiceResource(
            DataServiceManagementService service,
            StandardDataServiceModelCandidateService modelCandidateService,
            SpatialDataServiceModelCandidateService spatialModelCandidateService,
            SpatialDataServicePreviewService spatialPreviewService,
            SpatialDataServiceStyleService spatialStyleService,
            SpatialStyleFieldProfileService spatialStyleFieldProfileService,
            DataServiceRelatedModelService relatedModelService,
            DataServiceLineageQueryService lineageQueryService
    ) {
        this.service = service;
        this.modelCandidateService = modelCandidateService;
        this.spatialModelCandidateService = spatialModelCandidateService;
        this.spatialPreviewService = spatialPreviewService;
        this.spatialStyleService = spatialStyleService;
        this.spatialStyleFieldProfileService = spatialStyleFieldProfileService;
        this.relatedModelService = relatedModelService;
        this.lineageQueryService = lineageQueryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间模型候选")
    @Operation(summary = "查询空间服务模型候选", description = "分页返回可绑定到当前空间服务的已发布模型，并说明每个模型是否满足单 Geometry 字段、PostGIS 数据源和目标 GeoServer 等要求；可选择包含不可用候选及原因。")
    @GetMapping("/{id}/spatial-model-candidates")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public PageResponse<SpatialDataServiceModelCandidateResponse> spatialModelCandidates(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request,
            @Parameter(description = "是否同时返回当前不可用的候选项。") @RequestParam(defaultValue = "false") boolean includeUnavailable
    ) {
        return spatialModelCandidateService.search(id, request, includeUnavailable);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询列表")
    @Operation(summary = "查询数据服务", description = "按通用 Search DSL 分页查询数据服务，并返回服务类型、生命周期、目标引擎、部署摘要和网关发布摘要；不会访问运行时引擎。")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    public PageResponse<DataServiceSummaryResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查看详情")
    @Operation(summary = "查看数据服务详情", description = "返回服务基本信息、类型化定义、目标引擎、部署状态和所有网关发布状态；敏感连接信息和脚本运行凭据不会返回。")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    public DataServiceDetailResponse get(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间预览能力")
    @Operation(summary = "查询空间服务预览能力", description = "检查空间服务、模型、GeoServer 图层和边界是否可用于地图预览，返回显示坐标系、初始范围及不可用原因；仅支持空间服务。")
    @GetMapping("/{id}/spatial-preview")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialDataServicePreviewResponse spatialPreview(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return spatialPreviewService.inspect(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染地图预览")
    @Operation(summary = "渲染空间服务地图预览", description = "调用已部署空间服务所在 GeoServer 的 WMS，根据 EPSG:3857 bbox 和像素尺寸返回 PNG。服务和图层必须可预览；该二进制接口不属于系统 MCP 第一版支持范围。")
    @GetMapping(value = "/{id}/spatial-preview/map", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('service.view')")
    public ResponseEntity<byte[]> spatialPreviewMap(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Parameter(description = "EPSG:3857 地图范围，格式为 minX,minY,maxX,maxY。") @RequestParam String bbox,
            @Parameter(description = "预览图宽度，单位像素，范围 256～1600。") @RequestParam int width,
            @Parameter(description = "预览图高度，单位像素，范围 256～1200。") @RequestParam int height
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.render(id, bbox, width, height));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：读取图例")
    @Operation(summary = "读取空间服务图例", description = "调用已部署空间服务所在 GeoServer 返回当前图层样式的 PNG 图例。服务必须已启用并部署成功；该二进制接口不属于系统 MCP 第一版支持范围。")
    @GetMapping(value = "/{id}/spatial-preview/legend", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('service.view')")
    public ResponseEntity<byte[]> spatialPreviewLegend(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderLegend(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间样式")
    @Operation(summary = "查询空间服务样式", description = "返回空间服务当前样式模式、在线制图文档或上传 SLD、字段能力、样式版本及远端同步状态。仅支持绑定 GeoServer 的空间服务。")
    @GetMapping("/{id}/spatial-style")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialDataServiceStyleResponse spatialStyle(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return spatialStyleService.get(id);
    }

    /** Read-only query: compile draft SLD without persistence or remote execution. */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：读取空间样式 SLD")
    @Operation(summary = "编译空间样式 SLD", description = "校验请求中的在线制图文档并编译为 SLD 文本，不保存样式，也不访问 GeoServer。样式文档上限为 256 KiB；通用 Geometry 不支持在线编译。")
    @PostMapping("/{id}/actions/query-spatial-style-sld")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialStyleSldResponse querySpatialStyleSld(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody QuerySpatialStyleSldRequest request
    ) {
        return spatialStyleService.querySld(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改空间样式")
    @Operation(summary = "修改空间服务样式", description = "保存并激活在线制图文档，或切换到已有的上传 SLD。若服务已部署，仅更新待应用样式版本；需调用应用样式接口才会同步到 GeoServer。")
    @PostMapping("/{id}/actions/update-spatial-style")
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialDataServiceStyleResponse updateSpatialStyle(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateSpatialStyleRequest request
    ) {
        return spatialStyleService.updateCartography(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询样式字段分布")
    @Operation(summary = "分析空间样式字段分布", description = "从空间服务绑定模型的 PostgreSQL 物理表读取总数、非空数以及指定字段的唯一值频次或数值分布，供分类样式配置使用；每条统计 SQL 的超时为 10 秒。该只读操作会访问外部数据库，要求数据源已启用，但不要求服务已经部署或启用。")
    @PostMapping("/{id}/actions/query-spatial-style-field-profile")
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialStyleFieldProfileResponse querySpatialStyleFieldProfile(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody SpatialStyleFieldProfileRequest request
    ) {
        return spatialStyleFieldProfileService.profile(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染样式预览")
    @Operation(summary = "渲染在线样式预览", description = "校验并临时编译请求中的在线制图文档，再调用 GeoServer 按指定范围渲染 PNG；不保存样式、不改变已应用版本。该二进制接口不属于系统 MCP 第一版支持范围。")
    @PostMapping(
            value = "/{id}/actions/render-spatial-style-preview",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @PreAuthorize("hasAuthority('service.update')")
    public ResponseEntity<byte[]> renderSpatialStylePreview(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody RenderSpatialStylePreviewRequest request
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderDraft(id, request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染上传样式预览")
    @Operation(summary = "渲染上传 SLD 预览", description = "校验上传的 SLD 并临时绑定当前图层后调用 GeoServer 渲染 PNG；不保存文件、不改变当前样式。该文件和二进制接口不属于系统 MCP 第一版支持范围。")
    @PostMapping(
            value = "/{id}/actions/render-spatial-style-preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @PreAuthorize("hasAuthority('service.update')")
    public ResponseEntity<byte[]> renderUploadedSpatialStylePreview(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestParam("file") MultipartFile file,
            @Parameter(description = "EPSG:3857 地图范围，格式为 minX,minY,maxX,maxY。") @RequestParam String bbox,
            @Parameter(description = "预览图宽度，单位像素，范围 256～1600。") @RequestParam int width,
            @Parameter(description = "预览图高度，单位像素，范围 256～1200。") @RequestParam int height
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderUploadedDraft(id, file, bbox, width, height));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：上传 SLD 样式")
    @Operation(summary = "上传空间服务 SLD", description = "校验并保存一个非空、最多 512 KiB 的 UTF-8 SLD 1.0 文件，同时切换到 UPLOADED_SLD。文件扩展名必须为 .sld 或 .xml，禁止外部图片、字体和其他远程资源，并要求包含适合当前几何族的 Symbolizer。已部署服务不会自动更新 GeoServer；需继续调用应用样式。该文件接口不属于系统 MCP 第一版支持范围。")
    @PostMapping(value = "/{id}/actions/upload-spatial-sld", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialDataServiceStyleResponse uploadSpatialSld(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestParam("file") MultipartFile file
    ) {
        return spatialStyleService.upload(id, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：应用空间样式")
    @Operation(summary = "应用空间服务样式", description = "把当前样式版本写入 GeoServer 并绑定到已部署图层，然后更新应用版本和同步状态。服务尚未部署时拒绝执行；远端失败时记录失败状态并返回网关错误。")
    @PostMapping("/{id}/actions/apply-spatial-style")
    @PreAuthorize("hasAuthority('service.publish')")
    public SpatialDataServiceStyleResponse applySpatialStyle(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return spatialStyleService.apply(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询已发布网关服务")
    @Operation(summary = "查询已发布网关服务", description = "按访问模式返回本地 publicationStatus=PUBLISHED 的数据服务、网关路径和完整调用地址，按服务编码排序。不会实时访问网关，也不要求最近对账一致；SUBSCRIPTION_REQUIRED 结果可用于创建订阅。")
    @GetMapping("/gateway-publications")
    @PreAuthorize("hasAuthority('service.view')")
    public List<GatewayDataServicePublicationResponse> publishedGatewayServices(
            @Parameter(description = "网关访问模式：PUBLIC 匿名访问，SUBSCRIPTION_REQUIRED 仅允许已订阅消费者访问。") @RequestParam DataServiceAccessMode accessMode
    ) {
        return service.publishedGatewayServices(accessMode);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：创建")
    @Operation(summary = "创建数据服务", description = "创建标准表、SQL、脚本或空间服务草稿，可不提供定义，也可同时保存恰好一种匹配定义。始终校验编码、目录、引擎类型及引擎内路径；提供定义时还要求来源有效且数据源登记 READY。不会部署、执行 SQL/脚本或发布。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.create')")
    public DataServiceDetailResponse create(@Valid @RequestBody CreateDataServiceRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改")
    @Operation(summary = "修改数据服务", description = "整体修改草稿或已停用服务的名称、目录、目标引擎、路径、说明和可选完整定义。服务类型不可修改；四种定义都省略时保留现有定义。除非没有部署记录或 deploymentStatus=REMOVED，否则即使业务状态不是 ENABLED 也需先清理部署。")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse update(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateDataServiceRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改定义")
    @Operation(summary = "修改数据服务定义", description = "必须提交恰好一种与既有类型匹配的完整定义，并整体替换当前定义；重新校验来源模型、字段、参数和目标引擎登记。服务必须非 ENABLED，且没有部署记录或 deploymentStatus=REMOVED。不能用空请求删除定义。")
    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse updateDefinition(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataServiceDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询标准模型候选")
    @Operation(summary = "查询标准表服务模型候选", description = "分页返回可绑定到当前标准表服务的已发布模型，并说明数据源用途、目标引擎登记和模型状态等可用性；可选择包含不可用候选及原因。")
    @GetMapping("/{id}/standard-model-candidates")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public PageResponse<StandardDataServiceModelCandidateResponse> standardModelCandidates(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request,
            @Parameter(description = "是否同时返回当前不可用的候选项。") @RequestParam(defaultValue = "false") boolean includeUnavailable
    ) {
        return modelCandidateService.search(id, request, includeUnavailable);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询关联模型")
    @Operation(summary = "查询数据服务关联模型", description = "返回服务定义显式保存的模型引用及角色。标准表和空间服务返回一个主模型，SQL 服务返回用户选择的有序辅助引用，脚本服务返回空列表；SQL 引用不构成表访问白名单。")
    @GetMapping("/{id}/related-models")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public List<DataServiceRelatedModelResponse> relatedModels(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return relatedModelService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询表级血缘")
    @Operation(summary = "查询数据服务表级血缘", description = "仅支持标准表服务。以数据服务为焦点，从其当前标准服务定义关联的模型向上游遍历已持久化的任务血缘，返回限定深度的节点和边；其他服务类型、定义未配置或关联模型已删除时返回只含服务节点和原因告警的空图。要求同时具备服务、模型和任务查看权限。图最多 200 个节点、600 条关系，超过时通过 truncated 和 warnings 说明。")
    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse tableLineage(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Parameter(description = "从关联模型向上游跨越的任务转换深度，只支持 1 或 2；省略时为 2。") @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.tableLineage(id, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询字段血缘")
    @Operation(summary = "查询数据服务字段血缘", description = "仅支持标准表服务。从服务当前定义关联模型的指定字段向上游查询字段级任务血缘，并将服务暴露关系接到图中；字段必须属于关联模型，未被该服务实际暴露时返回字段摘要但不生成暴露关系。其他服务类型、定义未配置或关联模型已删除时返回只含服务节点和原因告警的空图。图最多 200 个节点、600 条关系，超过时显式标记截断。")
    @GetMapping("/{id}/lineage/fields/{fieldId}")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse fieldLineage(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Parameter(description = "模型字段 UUID。") @PathVariable UUID fieldId,
            @Parameter(description = "从字段向上游跨越的任务转换深度，只支持 1 或 2；省略时为 2。") @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.fieldLineage(id, fieldId, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：批量查询字段血缘")
    @Operation(summary = "批量查询数据服务字段血缘", description = "仅支持标准表服务。一次选择关联模型中的最多 50 个字段，返回合并后的上游字段血缘图及每个焦点字段的暴露状态、证据完整度和告警；省略字段列表时选择按模型字段顺序排列的前 20 个已暴露字段，空数组返回空焦点集合。只读 POST，不修改血缘数据。")
    @PostMapping("/{id}/lineage/actions/query-fields")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageFieldGraphResponse queryFieldLineage(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody QueryDataServiceFieldLineageRequest request
    ) {
        return lineageQueryService.fieldLineages(
                id, request.fieldIds(), request.depth() == null ? 2 : request.depth()
        );
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：测试 SQL")
    @Operation(summary = "测试 SQL 服务定义", description = "在指定已启用 JDBC 数据源上校验只读单语句 SQL、参数和模型关联，并执行最多 50 行的预览，省略 previewSize 时读取 20 行，JDBC 执行超时为 30 秒。不会创建或修改数据服务；外部查询在管理库事务之外执行。模型关联只用于治理信息，不限制 SQL 实际访问的表。可确认的 SQL 检查或执行失败返回 HTTP 200 且 valid=false；数据源或关联模型不满足前置条件时返回业务错误。连接会尝试设为只读，驱动不支持时最终安全边界仍由数据库账号权限提供。")
    @PostMapping("/actions/test-sql")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public SqlServiceTestResponse testSql(@Valid @RequestBody SqlServiceTestRequest request) {
        return service.testSql(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：启用")
    @Operation(summary = "启用并部署数据服务", description = "同步校验定义、来源、物理表或 SQL、数据源登记、引擎和访问策略，再登记部署修订并调用 Service Engine 或 GeoServer。远端成功后 status=ENABLED、deploymentStatus=DEPLOYED；远端失败仍返回 200，业务状态保持 DRAFT 或 DISABLED，deploymentStatus=FAILED 并携带错误。同一失败定义可直接重试并复用修订；要修改定义需先清理失败部署。")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse enable(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：发布")
    @Operation(summary = "发布数据服务到网关", description = "要求非空间服务处于 ENABLED，当前 revision 已在启用的 Engine 部署成功；将 engine.runtimeUrl 与 contextPath 作为上游，以指定 /open-api/v1/ 路径和访问模式发布。明确路径冲突返回 409；其他远端失败通常返回 200，并在 gatewayBindings 中记录 PUBLISH_FAILED，远端可能留下部分对象。")
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse publish(
            @Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody PublishDataServiceRequest request
    ) {
        return service.publish(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：对账网关状态")
    @Operation(summary = "对账数据服务网关状态", description = "要求至少一个网关绑定；逐个读取远端实际对象并与当前本地服务、引擎地址、路由和访问模式比较。远端检查失败通常仍返回 200，并在绑定中记录 CHECK_FAILED。该操作不修复远端，也不改变 publicationStatus、服务定义或引擎部署。")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse reconcileGateway(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：取消发布")
    @Operation(summary = "从网关取消发布数据服务", description = "要求服务仍为 ENABLED；逐个从 API Gateway 删除绑定，成功的绑定记录直接删除，服务和 Engine 部署保持在线。远端删除失败通常仍返回 200，对应绑定保留为 REMOVE_FAILED；需再次取消发布。网关业务冲突也记录在绑定错误中。")
    @PostMapping("/{id}/actions/unpublish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse unpublish(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.unpublish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：停用")
    @Operation(summary = "停用数据服务", description = "要求服务当前 ENABLED。先逐个撤销网关绑定；任一撤回失败时返回 200，保留 REMOVE_FAILED 绑定、Engine 部署和 ENABLED 状态。网关全部撤回后再移除 Engine/GeoServer 部署；移除失败同样返回 200，服务保持 ENABLED 且 deploymentStatus=FAILED。只有两层均确认移除后才置为 DISABLED/REMOVED。")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse disable(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：清理部署")
    @Operation(summary = "清理数据服务残留部署", description = "仅用于非 ENABLED 服务中 deploymentStatus=FAILED 或 PENDING 的部署残留，例如首次启用失败或进程中断；同步请求目标引擎移除运行时路由、图层和样式。失败仍返回 200 并保持 FAILED，成功置为 REMOVED；不会删除定义或改变 DRAFT/DISABLED。停用过程中移除失败且服务仍 ENABLED 时应再次调用停用。")
    @PostMapping("/{id}/actions/cleanup-deployment")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse cleanupDeployment(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.cleanupDeployment(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：删除")
    @Operation(summary = "删除数据服务", description = "永久删除已停用、已确认移除部署、没有网关绑定且没有消费者订阅的数据服务及其类型化定义。任一引用或残留状态存在时拒绝删除。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.delete')")
    public void delete(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}
