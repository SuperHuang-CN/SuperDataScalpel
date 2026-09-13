package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceService;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRelationQueryService;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.InspectJdbcQueryRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.KafkaTopicResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.JdbcQueryInspectionResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceRelationKind;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceRelatedModelResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceRelatedTaskResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceRelatedServiceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceTaskRelationRole;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicDetailResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-sources")
@Tag(name = "数据源管理")
public class DataSourceResource {

    private final DataSourceService service;
    private final DataSourceRelationQueryService relationQueryService;

    public DataSourceResource(DataSourceService service, DataSourceRelationQueryService relationQueryService) {
        this.service = service;
        this.relationQueryService = relationQueryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据源")
    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询数据源", description = "分页读取管理库中已登记的数据源及其配置摘要，不连接外部数据源，也不执行健康探测。")
    public PageResponse<DataSourceResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据源详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询数据源详情", description = "读取一个已登记数据源的配置详情和当前状态；敏感凭据只返回掩码或是否已配置，不返回明文。")
    public DataSourceResponse get(@Parameter(description = "数据源 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询使用当前数据源存储的模型")
    @GetMapping("/{id}/related-models")
    @PreAuthorize("hasAuthority('datasource.view') and hasAuthority('model.view')")
    @Operation(summary = "查询使用当前数据源存储的模型", description = "分页查询物理存储位于该数据源的模型，用于变更或删除数据源前评估直接依赖。")
    public PageResponse<DataSourceRelatedModelResponse> relatedModels(
            @Parameter(description = "要评估直接存储模型依赖的数据源 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return relationQueryService.relatedModels(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询直接或通过模型使用当前数据源的任务")
    @GetMapping("/{id}/related-tasks")
    @PreAuthorize("hasAuthority('datasource.view') and hasAuthority('task.view')")
    @Operation(summary = "查询直接或通过模型使用当前数据源的任务", description = "分页查询直接引用该数据源或经模型间接引用它的任务，可按输入/输出角色和引用方式筛选。")
    public PageResponse<DataSourceRelatedTaskResponse> relatedTasks(
            @Parameter(description = "要评估任务依赖的数据源 UUID。") @PathVariable UUID id,
            @Parameter(description = "可选角色筛选：INPUT 数据源向任务提供输入，OUTPUT 任务向数据源写出；为空时包含两者。") @RequestParam(required = false) DataSourceTaskRelationRole role,
            @Parameter(description = "可选引用方式筛选：DIRECT 任务定义直接引用数据源，VIA_MODEL 任务引用存储在该数据源上的模型；为空时包含两者。") @RequestParam(required = false) DataSourceRelationKind relationKind,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return relationQueryService.relatedTasks(id, role, relationKind, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询直接或通过模型使用当前数据源的数据服务")
    @GetMapping("/{id}/related-services")
    @PreAuthorize("hasAuthority('datasource.view') and hasAuthority('service.view')")
    @Operation(summary = "查询直接或通过模型使用当前数据源的数据服务", description = "分页查询直接引用该数据源或经模型间接引用它的数据服务，用于评估配置变更和删除影响。")
    public PageResponse<DataSourceRelatedServiceResponse> relatedServices(
            @Parameter(description = "要评估数据服务依赖的数据源 UUID。") @PathVariable UUID id,
            @Parameter(description = "可选引用方式筛选：DIRECT 服务定义直接绑定数据源，VIA_MODEL 服务引用存储在该数据源上的模型；为空时包含两者。") @RequestParam(required = false) DataSourceRelationKind relationKind,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return relationQueryService.relatedServices(id, relationKind, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增数据源", prerequisites = "先查询数据库类型与连接参数；可先测试未保存连接。", relatedOperations = {"GET /api/v1/data-source-types", "POST /api/v1/data-sources/actions/test"})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "新增数据源", description = "校验类型和连接参数后保存数据源及加密凭据；不会隐式测试连接，需要可用性验证时另行调用测试接口。")
    public DataSourceResponse create(@Valid @RequestBody CreateDataSourceRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改数据源")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改数据源", description = "整体更新已登记数据源的名称、用途、启停状态、连接配置和凭据；不会隐式测试连接。已有引用保持不变，受影响的服务引擎注册会标记为 OUTDATED；已被 GeoServer、服务引擎或已启用数据服务使用时，部分类型、用途和停用变更会被拒绝。")
    public DataSourceResponse update(
            @Parameter(description = "数据源 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataSourceRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除数据源", prerequisites = "先检查关联模型、任务和数据服务；被引用的数据源不能直接删除。", relatedOperations = {"GET /api/v1/data-sources/{id}/related-models", "GET /api/v1/data-sources/{id}/related-tasks", "GET /api/v1/data-sources/{id}/related-services"})
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除数据源", description = "删除管理库中的数据源配置和凭据，不删除外部系统中的数据。存在服务引擎注册、API 或空间资源、模型、SQL/脚本服务、任务直接引用或 Spark JAR 资源绑定时拒绝删除。")
    public void delete(@Parameter(description = "数据源 UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "测试未保存的数据源连接")
    @PostMapping("/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试未保存的数据源连接", description = "使用请求中的临时配置执行类型专属探测，不创建数据源，也不保存凭据。JDBC 建立数据库连接；Kafka 读取 Cluster ID；普通 HTTP API 对基地址发起 GET；ArcGIS/WFS 读取服务描述；S3 在目标 Bucket 和根前缀下写入、列出并删除临时对象，清理失败时可能残留 .datascalpel-connection-test/ 对象。")
    public ConnectionTestResponse test(@Valid @RequestBody TestDataSourceConnectionRequest request) {
        return service.test(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "测试已保存的数据源连接")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试已保存的数据源连接", description = "读取已保存连接和凭据执行类型专属探测，不修改管理库配置。JDBC 建立数据库连接；Kafka 读取 Cluster ID；普通 HTTP API 对基地址发起 GET；ArcGIS/WFS 读取服务描述；S3 在目标 Bucket 和根前缀下写入、列出并删除临时对象，清理失败时可能残留 .datascalpel-connection-test/ 对象。")
    public ConnectionTestResponse test(@Parameter(description = "数据源 UUID") @PathVariable UUID id) {
        return service.test(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据源的库和 Schema")
    @GetMapping("/{id}/namespaces")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "查询数据源的库和 Schema", description = "连接指定 JDBC 数据源并只读发现可访问的 Catalog 或 Schema；结果按显示名称不区分大小写排序，并标记连接配置解析出的默认命名空间。该管理探测不要求数据源处于启用状态。")
    public List<NamespaceResponse> listNamespaces(@Parameter(description = "JDBC 数据源 UUID") @PathVariable UUID id) {
        return service.listNamespaces(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据源中的表")
    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "查询数据源中的表", description = "连接指定 JDBC 数据源并只读发现表和可选视图，返回数量由 limit 限制且最多 500 项，并通过 truncated 表示是否还有结果。普通 JDBC 在截断后再排序，因此截断结果只对本次返回集合有序；TDengine 只发现超级表并按名称排序。该管理探测不要求数据源处于启用状态。")
    public TableListResponse listTables(
            @Parameter(description = "JDBC 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "限定 Catalog；数据库不使用 Catalog 时省略") @RequestParam(required = false) String catalog,
            @Parameter(description = "限定 Schema；数据库不使用 Schema 时省略") @RequestParam(required = false) String schema,
            @Parameter(description = "按表名或视图名进行不区分大小写的片段筛选") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否在结果中包含视图") @RequestParam(defaultValue = "false") boolean includeViews,
            @Parameter(description = "最多返回的表和视图数量，范围 1 到 500") @RequestParam(defaultValue = "500") int limit
    ) {
        if (limit < 1 || limit > 500) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "物理表查询条数必须在 1 到 500 之间"
            );
        }
        return service.listTables(id, catalog, schema, keyword, includeViews, limit);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "读取数据表元数据")
    @GetMapping("/{id}/table-metadata")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "读取数据表元数据", description = "从指定 JDBC 数据源读取表或视图的列、主键、索引和可用唯一键等元数据，不扫描或修改业务数据。Catalog 或 Schema 省略时按方言和连接配置解析默认值；该管理探测不要求数据源处于启用状态。")
    public TableMetadataResponse readTable(
            @Parameter(description = "JDBC 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "表所属 Catalog；数据库不使用 Catalog 时省略") @RequestParam(required = false) String catalog,
            @Parameter(description = "表所属 Schema；数据库不使用 Schema 时省略") @RequestParam(required = false) String schema,
            @Parameter(description = "要读取结构的表或视图名称") @RequestParam String table
    ) {
        return service.readTable(id, catalog, schema, table);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "分析只读 JDBC 查询结果字段")
    @PostMapping("/{id}/actions/inspect-query")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "分析只读 JDBC 查询结果字段", description = "解析并校验单条只读 SELECT/WITH 查询，优先从 PreparedStatement 读取结果元数据；驱动无法直接提供时会在只读连接中执行查询并把最大结果行数限制为 1，但不返回读取到的业务数据。仅支持已启用、具有 SOURCE 用途的 PostgreSQL、HighGo、MySQL、openGauss 或人大金仓，超时为 30 秒；查询仍可能触发数据库函数自身的外部行为。")
    public JdbcQueryInspectionResponse inspectQuery(
            @Parameter(description = "JDBC 数据源 UUID") @PathVariable UUID id,
            @Valid @RequestBody InspectJdbcQueryRequest request
    ) {
        return service.inspectQuery(id, request.sql());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览数据表数据")
    @GetMapping("/{id}/table-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览数据表数据", description = "从指定 JDBC 表或视图读取 limit+1 行以判断是否截断，响应最多返回 100 行；语句级超时尝试设为 15 秒，但部分驱动不支持。查询不带 ORDER BY，顺序不构成稳定排序承诺；该管理探测不要求数据源处于启用状态。")
    public TablePreviewResponse preview(
            @Parameter(description = "JDBC 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "表所属 Catalog；数据库不使用 Catalog 时省略") @RequestParam(required = false) String catalog,
            @Parameter(description = "表所属 Schema；数据库不使用 Schema 时省略") @RequestParam(required = false) String schema,
            @Parameter(description = "要预览的表或视图名称") @RequestParam String table,
            @Parameter(description = "最多返回的样例行数，范围 1 到 100") @RequestParam(defaultValue = "50") int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预览行数必须在 1 到 100 之间"
            );
        }
        return service.preview(id, catalog, schema, table, limit);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Kafka Topic")
    @GetMapping("/{id}/kafka-topics")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 Kafka Topic", description = "连接已启用的 Kafka 数据源并只读列出 Topic，默认排除内部 Topic；按名称排序后最多返回前 200 项，响应不提供截断标志。分区详情读取失败时仍返回 Topic，并令 metadataAvailable=false。Kafka 管理调用最长等待 120 秒。")
    public List<KafkaTopicResponse> listKafkaTopics(
            @Parameter(description = "Kafka 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "按 Topic 名称进行不区分大小写的片段筛选") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含 Kafka 内部 Topic") @RequestParam(defaultValue = "false") boolean includeInternal
    ) {
        return service.listKafkaTopics(id, keyword, includeInternal);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 TDengine TMQ Topic")
    @GetMapping("/{id}/tmq-topics")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "查询 TDengine TMQ Topic", description = "连接 TDENGINE_WEBSOCKET 数据源并只读发现 TMQ Topic，按名称排序后最多返回 500 项且响应不提供截断标志。每项会解析定义并读取超级表结构；只有不含元数据消息、定义为 SELECT * FROM database.supertable 且字段可无损映射的 Topic 才标记 supported=true。不消费消息，也不要求数据源处于启用状态。")
    public List<TdEngineTmqTopicResponse> listTdEngineTmqTopics(
            @Parameter(description = "TDengine 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "按 Topic 名称进行不区分大小写的片段筛选") @RequestParam(required = false) String keyword
    ) {
        return service.listTdEngineTmqTopics(id, keyword);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "读取 TDengine TMQ Topic 详情")
    @GetMapping("/{id}/tmq-topic")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "读取 TDengine TMQ Topic 详情", description = "读取指定 TDengine TMQ Topic 的定义和可推导字段信息，不消费消息，也不改变订阅进度。")
    public TdEngineTmqTopicDetailResponse readTdEngineTmqTopic(
            @Parameter(description = "TDengine 数据源 UUID") @PathVariable UUID id,
            @Parameter(description = "要读取的 TMQ Topic 名称") @RequestParam String topic
    ) {
        return service.readTdEngineTmqTopic(id, topic);
    }
}
