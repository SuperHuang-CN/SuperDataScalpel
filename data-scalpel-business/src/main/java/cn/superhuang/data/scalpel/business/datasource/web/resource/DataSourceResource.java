package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.datasource.service.DataSourceService;
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
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
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

    public DataSourceResource(DataSourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询数据源")
    public PageResponse<DataSourceResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询数据源详情")
    public DataSourceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "新增数据源")
    public DataSourceResponse create(@Valid @RequestBody CreateDataSourceRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改数据源")
    public DataSourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataSourceRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除数据源")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试未保存的数据源连接")
    public ConnectionTestResponse test(@Valid @RequestBody TestDataSourceConnectionRequest request) {
        return service.test(request);
    }

    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试已保存的数据源连接")
    public ConnectionTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    @GetMapping("/{id}/namespaces")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "查询数据源的库和 Schema")
    public List<NamespaceResponse> listNamespaces(@PathVariable UUID id) {
        return service.listNamespaces(id);
    }

    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "查询数据源中的表")
    public TableListResponse listTables(
            @PathVariable UUID id,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeViews
    ) {
        return service.listTables(id, catalog, schema, keyword, includeViews);
    }

    @GetMapping("/{id}/table-metadata")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "读取数据表元数据")
    public TableMetadataResponse readTable(
            @PathVariable UUID id,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String table
    ) {
        return service.readTable(id, catalog, schema, table);
    }

    @PostMapping("/{id}/actions/inspect-query")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "分析只读 JDBC 查询结果字段")
    public JdbcQueryInspectionResponse inspectQuery(
            @PathVariable UUID id,
            @Valid @RequestBody InspectJdbcQueryRequest request
    ) {
        return service.inspectQuery(id, request.sql());
    }

    @GetMapping("/{id}/table-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览数据表数据")
    public TablePreviewResponse preview(
            @PathVariable UUID id,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String table,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预览行数必须在 1 到 100 之间"
            );
        }
        return service.preview(id, catalog, schema, table, limit);
    }

    @GetMapping("/{id}/kafka-topics")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 Kafka Topic")
    public List<KafkaTopicResponse> listKafkaTopics(
            @PathVariable UUID id,
            @RequestParam(required = false) String keyword
    ) {
        return service.listKafkaTopics(id, keyword);
    }
}
