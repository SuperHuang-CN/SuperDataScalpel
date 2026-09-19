package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetContent;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.request.CreateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableSpatialReferenceRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetFileResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetPreviewResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetSchemaResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableLoadSubmissionResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableSourceResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetUploadResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/file-datasets")
@Tag(name = "文件数据集管理")
public class FileDatasetResource {

    private final FileDatasetService service;

    public FileDatasetResource(FileDatasetService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集",
            keywords = {"文件数据集", "文件", "逻辑表", "解析"},
            relatedOperations = {"GET /api/v1/file-datasets/{id}", "GET /api/v1/file-datasets/{id}/tables"})
    @GetMapping
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集", description = "使用通用 Search DSL 分页查询数据集基础信息、类型、共享解析参数和汇总状态；不读取对象内容或预览行。")
    public PageResponse<FileDatasetResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集详情",
            keywords = {"文件数据集", "详情", "解析参数", "文件统计", "表统计"})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集详情", description = "返回数据集元数据、共享解析参数、文件/逻辑表统计以及解析参数是否已锁定；不返回文件二进制和表数据。")
    public FileDatasetResponse get(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建空文件数据集",
            keywords = {"文件数据集", "创建", "解析参数"})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('filedataset.create')")
    @Operation(summary = "创建空文件数据集", description = "创建指定类型和共享解析参数的空数据集并返回 201；不会上传文件、创建逻辑表或开始解析。")
    public FileDatasetResponse create(@Valid @RequestBody CreateFileDatasetRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改文件数据集信息和共享解析参数",
            prerequisites = "修改解析参数时，数据集没有文件、逻辑表或非终态解析任务。")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "修改文件数据集信息和共享解析参数", description = "整体替换名称、目录、说明和同类型 parsingOptions；数据集 type 不能修改。名称、目录和说明始终可改；只有解析选项的规范化 JSON 实际变化时才检查锁定，一旦存在文件、逻辑表或 QUEUED/RUNNING 作业便返回 409。")
    public FileDatasetResponse update(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateFileDatasetRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集的物理文件")
    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集的物理文件", description = "使用通用 Search DSL 分页查询数据集当前物理文件、格式、大小、存储形态和准备状态；不返回对象 Key 或文件内容。")
    public PageResponse<FileDatasetFileResponse> files(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchFiles(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "向文件数据集上传一个文件并创建逻辑表")
    @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "向文件数据集上传文件并创建逻辑表", description = "接口参数虽为列表，但当前每次必须且只能上传一个文件。单表格式会创建新逻辑表；EXCEL 同步发现工作表后逐表排队解析；GDB、SHP、GPKG 先异步准备或发现。上传不会按名称自动追加到现有表。EXCEL、GDB、GPKG 数据集只能保留一个物理文件，解析异步执行；该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public FileDatasetUploadResponse uploadFiles(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "上传文件列表；当前长度必须恰好为 1。单个文件和请求体默认均受 Spring multipart 1 GiB 上限约束，部署可通过 DATASCALPEL_FILE_MAX_SIZE 和 DATASCALPEL_FILE_MAX_REQUEST_SIZE 调整。") @RequestPart("files") List<MultipartFile> files
    ) {
        return service.uploadFiles(id, files);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "替换物理文件并重建其逻辑表")
    @PostMapping(path = "/{id}/files/{fileId}/actions/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "替换物理文件并重建其逻辑表", description = "仅用于 EXCEL、GDB、GPKG 整文件来源；目标文件关联的任一表被 Canvas 引用或存在 RUNNING 作业时拒绝，QUEUED 作业会取消。提交后立即删除旧逻辑表和字段并创建或排队发现新表，后续准备或解析失败不会恢复旧表。旧对象在数据库提交后尽力清理，清理失败可能残留孤儿对象。该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public FileDatasetUploadResponse replaceFile(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件 UUID。") @PathVariable UUID fileId,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file
    ) {
        return service.replaceFile(id, fileId, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除物理文件及其数据来源")
    @PostMapping("/{id}/files/{fileId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "删除物理文件及其数据来源", description = "所有文件格式均可删除。删除该文件贡献的全部来源；逻辑表仍有其他来源时保留并压实来源顺序，失去最后来源时连同字段一起删除。仅将被删除的表受 Canvas 引用时拒绝；目标文件的 QUEUED 作业会取消，RUNNING 作业或其他文件正在装载受影响表时返回 409。数据库删除不可恢复，对象内容在提交后尽力清理，清理失败可能残留孤儿对象。")
    public void deleteFile(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id, @Parameter(description = "必须属于该数据集的文件 UUID。") @PathVariable UUID fileId) {
        service.deleteFile(id, fileId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载文件数据集物理文件")
    @GetMapping("/{id}/files/{fileId}/content")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "下载文件数据集物理文件", description = "以附件形式流式返回当前原始文件；该二进制接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> content(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id, @Parameter(description = "必须属于该数据集的文件 UUID。") @PathVariable UUID fileId) {
        FileDatasetContent content = service.openContent(id, fileId);
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = content.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(toMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(
                                content.originalFileName(), StandardCharsets.UTF_8
                        ).build().toString()
                );
        if (content.sizeBytes() >= 0) {
            response.contentLength(content.sizeBytes());
        }
        return response.body(body);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集的逻辑表",
            keywords = {"文件数据集", "逻辑表", "Schema", "解析状态"})
    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集的逻辑表", description = "使用通用 Search DSL 分页查询逻辑表、解析状态、来源数量、总行数、Schema 指纹和预览能力；不读取数据行。")
    public PageResponse<FileDatasetTableResponse> tables(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchTables(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集逻辑表详情")
    @GetMapping("/{id}/tables/{tableId}")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集逻辑表详情", description = "返回指定逻辑表的当前解析状态、权威 Schema 摘要、来源统计和空间参考确认；不返回预览数据。")
    public FileDatasetTableResponse table(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id, @Parameter(description = "必须属于该数据集的逻辑表 UUID。") @PathVariable UUID tableId) {
        return service.getTable(id, tableId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改逻辑表名称")
    @PostMapping("/{id}/tables/{tableId}/actions/update")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "修改逻辑表名称", description = "名称去除首尾空白并将内部连续空白转换为下划线；同一文件数据集内忽略大小写且不能重名。只修改用户可读名称，稳定编码、Schema、来源和解析状态保持不变。")
    public FileDatasetTableResponse updateTable(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Valid @RequestBody UpdateFileDatasetTableRequest request
    ) {
        return service.updateTable(id, tableId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "确认空间表的 EPSG 并重新解析 Schema",
            prerequisites = "逻辑表来自支持空间参考确认的格式，全部当前来源可读取，且文件声明不与请求 EPSG 冲突。")
    @PostMapping("/{id}/tables/{tableId}/actions/update-spatial-reference")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "确认空间表的 EPSG 并重新解析 Schema", description = "仅支持已有数据且没有在途装载的 GDB 或 SHP 表。提供 EPSG 作为文件无法识别 CRS 时的回退值，不能覆盖文件中可识别且不同的 EPSG；事务外重新读取全部当前来源，按每个来源最多保留 1000 条样本并校验 Schema 兼容，随后以并发快照校验原子替换字段 Schema。只确认坐标含义，不转换坐标。")
    public FileDatasetTableResponse updateTableSpatialReference(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Valid @RequestBody UpdateFileDatasetTableSpatialReferenceRequest request
    ) {
        return service.updateTableSpatialReference(id, tableId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询逻辑表的数据来源")
    @GetMapping("/{id}/tables/{tableId}/sources")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询逻辑表的数据来源", description = "按 sourceOrder 返回当前已生效来源分片及行数、Schema 指纹和激活时间；不包含临时校验文件和历史来源。")
    public List<FileDatasetTableSourceResponse> tableSources(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId
    ) {
        return service.tableSources(id, tableId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "向逻辑表追加一个文件来源")
    @PostMapping(
            path = "/{id}/tables/{tableId}/actions/append",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "向逻辑表追加一个文件来源", description = "仅支持已有当前来源、无在途装载且类型不是 EXCEL/GDB/GPKG 的表。新文件先独立排队，旧来源继续生效；后台按格式校验内容与权威 Schema，最多保留 1000 条样本。成功后作为最后一个来源参与 UNION ALL，不去重、不 Upsert、不演进 Schema；最终失败会保留原表并清理临时文件记录和对象。返回 202；该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public FileDatasetTableLoadSubmissionResponse append(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file
    ) {
        return service.append(id, tableId, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "全量覆盖逻辑表的全部当前来源")
    @PostMapping(
            path = "/{id}/tables/{tableId}/actions/replace-data",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "全量覆盖逻辑表的全部当前来源", description = "仅支持已有当前来源、无在途装载且类型不是 EXCEL/GDB/GPKG 的表。异步校验期间旧来源继续生效；后台按格式校验内容与权威 Schema，成功后原子改为单一新来源，并在提交后尽力清理不再使用的旧文件对象。最终失败保留原表并清理临时文件；对象清理失败可能残留孤儿对象。返回 202；该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public FileDatasetTableLoadSubmissionResponse replaceData(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file
    ) {
        return service.replaceData(id, tableId, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "替换逻辑表中的一个当前来源")
    @PostMapping(
            path = "/{id}/tables/{tableId}/sources/{sourceId}/actions/replace",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "替换逻辑表中的一个当前来源", description = "仅支持已有当前来源、无在途装载且类型不是 EXCEL/GDB/GPKG 的表，sourceId 必须仍是当前来源。异步校验期间旧来源继续生效；成功后原地更新指定来源并保持来源 UUID 和 sourceOrder，最终失败保留旧来源并清理临时文件。旧对象在提交后尽力清理。返回 202；该 multipart 接口不属于系统 MCP 第一版支持范围。")
    public FileDatasetTableLoadSubmissionResponse replaceSource(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Parameter(description = "必须属于该逻辑表的当前来源 UUID。") @PathVariable UUID sourceId,
            @Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file
    ) {
        return service.replaceSource(id, tableId, sourceId, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除表数据来源")
    @PostMapping("/{id}/tables/{tableId}/sources/{sourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "删除表数据来源", description = "GPKG 不支持表级来源删除；其他类型要求表没有在途装载。删除指定当前来源后压实剩余 sourceOrder；仅当删除最后一个来源将连同逻辑表和字段一起删除时，才检查并拒绝 Canvas 下游引用。来源文件没有其他来源或非终态作业引用时一并删除记录，并在提交后尽力清理对象。")
    public void deleteSource(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Parameter(description = "必须属于该逻辑表的当前来源 UUID。") @PathVariable UUID sourceId
    ) {
        service.deleteSource(id, tableId, sourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询逻辑表 Schema",
            keywords = {"文件数据集", "逻辑表", "字段", "Schema", "Geometry"})
    @GetMapping("/{id}/tables/{tableId}/schema")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询逻辑表 Schema", description = "返回当前权威字段顺序、平台类型、可空性和完整 Geometry 定义；Schema 来自所有当前来源严格兼容后的结果。")
    public FileDatasetSchemaResponse schema(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id, @Parameter(description = "必须属于该数据集的逻辑表 UUID。") @PathVariable UUID tableId) {
        return service.schema(id, tableId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览已解析逻辑表",
            keywords = {"文件数据集", "数据预览", "逻辑表"})
    @GetMapping("/{id}/tables/{tableId}/preview")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "预览已解析逻辑表", description = "按当前来源顺序读取最多 limit 行；任一来源无法安全读取时整表失败。空间格式只返回属性字段，不返回 Geometry 值。")
    public FileDatasetPreviewResponse preview(
            @Parameter(description = "文件数据集 UUID。") @PathVariable UUID id,
            @Parameter(description = "文件数据表 UUID。") @PathVariable UUID tableId,
            @Parameter(description = "最多返回的预览行数，范围 1 到 100，默认 50。") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return service.preview(id, tableId, limit);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除文件数据集及其全部文件和表",
            prerequisites = "数据集逻辑表没有 Canvas 下游引用，且文件准备和表解析作业均未处于 RUNNING。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.delete')")
    @Operation(summary = "删除文件数据集及其全部文件和表", description = "逻辑表被 Canvas 引用或任一准备/解析作业处于 RUNNING 时返回 409；QUEUED 作业会取消。随后永久删除数据集、文件、逻辑表、字段和来源记录并返回 204，不保留回收站。对象内容在数据库提交后尽力清理，清理失败不会回滚已完成的数据库删除，可能残留孤儿对象。")
    public void delete(@Parameter(description = "文件数据集 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }

    private static MediaType toMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
