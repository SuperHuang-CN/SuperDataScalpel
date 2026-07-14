package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final SearchEngine searchEngine;
    private final DirectoryService directoryService;
    private final DataSourceRuntimeService runtimeService;
    private final DataModelRepository dataModelRepository;

    public DataSourceService(
            DataSourceRepository repository,
            SearchEngine searchEngine,
            DirectoryService directoryService,
            DataSourceRuntimeService runtimeService,
            DataModelRepository dataModelRepository
    ) {
        this.repository = repository;
        this.searchEngine = searchEngine;
        this.directoryService = directoryService;
        this.runtimeService = runtimeService;
        this.dataModelRepository = dataModelRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataSourceResponse> search(SearchRequest request) {
        Page<DataSource> result = searchEngine.search(request, DataSource.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(DataSourceResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataSourceResponse get(UUID id) {
        return DataSourceResponse.from(requireDataSource(id));
    }

    @Transactional
    public DataSourceResponse create(CreateDataSourceRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源编码已存在");
        }
        runtimeService.validateConfiguration(request.databaseType(), request.connection());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, request.directoryId());
        DataSource dataSource = DataSource.create(
                code,
                request.name(),
                request.directoryId(),
                request.purposes(),
                request.databaseType(),
                request.enabled() == null || request.enabled(),
                request.description(),
                connectionForCreate(request.connection())
        );
        return DataSourceResponse.from(repository.saveAndFlush(dataSource));
    }

    @Transactional
    public DataSourceResponse update(UUID id, UpdateDataSourceRequest request) {
        DataSource dataSource = requireDataSource(id);
        runtimeService.validateConfiguration(request.databaseType(), request.connection());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, request.directoryId());
        dataSource.update(
                request.name(),
                request.directoryId(),
                request.purposes(),
                request.databaseType(),
                request.enabled(),
                request.description(),
                connectionForUpdate(request.connection())
        );
        return DataSourceResponse.from(repository.saveAndFlush(dataSource));
    }

    @Transactional
    public void delete(UUID id) {
        if (dataModelRepository.existsByStorageDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被模型使用，不能删除");
        }
        repository.delete(requireDataSource(id));
    }

    public ConnectionTestResponse test(TestDataSourceConnectionRequest request) {
        return runtimeService.test(request);
    }

    public ConnectionTestResponse test(UUID id) {
        return runtimeService.test(id);
    }

    public java.util.List<NamespaceResponse> listNamespaces(UUID id) {
        return runtimeService.listNamespaces(id);
    }

    public TableListResponse listTables(
            UUID id,
            String catalog,
            String schema,
            String keyword,
            boolean includeViews
    ) {
        return runtimeService.listTables(id, catalog, schema, keyword, includeViews);
    }

    public TableMetadataResponse readTable(UUID id, String catalog, String schema, String table) {
        return runtimeService.readTable(id, catalog, schema, table);
    }

    public TablePreviewResponse preview(UUID id, String catalog, String schema, String table, int limit) {
        return runtimeService.preview(id, catalog, schema, table, limit);
    }

    private DataSource requireDataSource(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }

    private static DataSourceConnection connectionForCreate(DataSourceConnectionRequest request) {
        return DataSourceConnection.create(
                request.host().trim(),
                request.port(),
                request.databaseName().trim(),
                normalizeOptional(request.schemaName()),
                request.username().trim(),
                request.password(),
                request.options()
        );
    }

    private static DataSourceConnection connectionForUpdate(DataSourceConnectionRequest request) {
        return connectionForCreate(request);
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
