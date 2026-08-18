package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.JdbcDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.KafkaDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.S3DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.HttpApiDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestDiagnosticResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceTypeResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.KafkaTopicResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.JdbcQueryInspectionResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicDetailResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicResponse;
import cn.superhuang.data.scalpel.business.datasource.service.http.ConnectionProbeResult;
import cn.superhuang.data.scalpel.business.datasource.service.http.HttpApiConnectorRegistry;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlyQueryFingerprint;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.SaslConfigs;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Executes short-lived, read-only operations against a registered external database. */
@Service
public class DataSourceRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceRuntimeService.class);
    private static final int TABLE_LIST_LIMIT = 500;
    private static final long KAFKA_API_TIMEOUT_SECONDS = 120;
    private static final String KAFKA_REQUEST_TIMEOUT_MS = "60000";
    private static final String KAFKA_API_TIMEOUT_MS = "120000";
    private static final String KAFKA_CONNECTION_SETUP_TIMEOUT_MS = "30000";
    private static final String KAFKA_CONNECTION_SETUP_TIMEOUT_MAX_MS = "120000";

    private final DataSourceRepository repository;
    private final DialectRegistry registry;
    private final DatabaseInspector inspector;
    private final JdbcQueryInspector queryInspector;
    private final DataSourceCredentialCipher credentialCipher;
    private final HttpApiConnectorRegistry httpApiConnectors;
    private final SpatialServiceClient spatialServiceClient;

    public DataSourceRuntimeService(
            DataSourceRepository repository,
            DialectRegistry registry,
            DatabaseInspector inspector,
            JdbcQueryInspector queryInspector,
            DataSourceCredentialCipher credentialCipher,
            HttpApiConnectorRegistry httpApiConnectors,
            SpatialServiceClient spatialServiceClient
    ) {
        this.repository = repository;
        this.registry = registry;
        this.inspector = inspector;
        this.queryInspector = queryInspector;
        this.credentialCipher = credentialCipher;
        this.httpApiConnectors = httpApiConnectors;
        this.spatialServiceClient = spatialServiceClient;
    }

    public List<DataSourceTypeResponse> dataSourceTypes() {
        List<DataSourceTypeResponse> result = new java.util.ArrayList<>(registry.all().stream()
                .map(dialect -> DataSourceTypeResponse.jdbc(
                        dialect.definition(), inspector.isDriverAvailable(dialect.definition().id())
                ))
                .toList());
        result.add(DataSourceTypeResponse.kafka());
        result.add(DataSourceTypeResponse.s3());
        result.add(DataSourceTypeResponse.httpApi());
        result.add(DataSourceTypeResponse.arcgisRest());
        result.add(DataSourceTypeResponse.wfs());
        return List.copyOf(result);
    }

    public ConnectionTestResponse test(TestDataSourceConnectionRequest request) {
        if (request.type().connectionKind() == DataSourceConnectionKind.HTTP_API
                && request.connection() instanceof HttpApiDataSourceConnectionRequest httpApi) {
            cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection connection =
                    DataSourceService.buildHttpApiConnection(
                            httpApi, null, request.type(), credentialCipher);
            HttpApiContracts.RuntimeConnection runtimeConnection = runtimeConnection(connection);
            return isSpatial(request.type())
                    ? testSpatialService("draft", request.type(), runtimeConnection)
                    : testHttpApi("draft", runtimeConnection);
        }
        if (request.type().connectionKind() == DataSourceConnectionKind.KAFKA
                && request.connection() instanceof KafkaDataSourceConnectionRequest kafka) {
            validateKafka(kafka);
            return testKafka("draft", kafkaSettings(kafka));
        }
        if (request.type().connectionKind() == DataSourceConnectionKind.S3
                && request.connection() instanceof S3DataSourceConnectionRequest s3) {
            return testS3("draft", s3Settings(s3));
        }
        JdbcDataSourceConnectionRequest connection = requireJdbc(request.type(), request.connection());
        return testConnection("draft", request.type().name(), toConfig(connection));
    }

    public void validateConfiguration(DataSourceType type, DataSourceConnectionRequest connection) {
        if (type.connectionKind() != connection.kind()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源类型与连接配置不匹配");
        }
        if (type.connectionKind() != DataSourceConnectionKind.JDBC) {
            if (type.connectionKind() == DataSourceConnectionKind.KAFKA) {
                validateKafka((KafkaDataSourceConnectionRequest) connection);
            }
            return;
        }
        try {
            registry.require(type.name()).createConnectionSpec(toConfig((JdbcDataSourceConnectionRequest) connection));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public ConnectionTestResponse test(UUID id) {
        DataSource dataSource = requireDataSource(id);
        if (dataSource.getType().connectionKind() == DataSourceConnectionKind.HTTP_API) {
            HttpApiContracts.RuntimeConnection runtimeConnection = runtimeConnection(dataSource.getConnection());
            return isSpatial(dataSource.getType())
                    ? testSpatialService("dataSourceId=" + id, dataSource.getType(), runtimeConnection)
                    : testHttpApi("dataSourceId=" + id, runtimeConnection);
        }
        if (dataSource.getType().connectionKind() == DataSourceConnectionKind.KAFKA) {
            return testKafka("dataSourceId=" + id, kafkaSettings(dataSource));
        }
        if (dataSource.getType().connectionKind() == DataSourceConnectionKind.S3) {
            return testS3("dataSourceId=" + id, s3Settings(dataSource));
        }
        requireJdbc(dataSource);
        return testConnection(
                "dataSourceId=" + id,
                dataSource.getType().name(),
                dataSource.getConnection().toJdbcConnectionConfig()
        );
    }

    public List<NamespaceResponse> listNamespaces(UUID id) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        try {
            return inspector.listNamespaces(
                            dataSource.getType().name(),
                            dataSource.getConnection().toJdbcConnectionConfig()
                    ).stream()
                    .map(NamespaceResponse::from)
                    .toList();
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TableListResponse listTables(
            UUID id,
            String catalog,
            String schema,
            String keyword,
            boolean includeViews
    ) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        try {
            return TableListResponse.from(inspector.listTables(
                    dataSource.getType().name(),
                    dataSource.getConnection().toJdbcConnectionConfig(),
                    new TableQuery(catalog, schema, keyword, includeViews, TABLE_LIST_LIMIT)
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TableMetadataResponse readTable(UUID id, String catalog, String schema, String table) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        try {
            return TableMetadataResponse.from(inspector.readTable(
                    dataSource.getType().name(),
                    config,
                    resolvedTable(dialect, config, catalog, schema, table)
            ), dialect);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TablePreviewResponse preview(UUID id, String catalog, String schema, String table, int limit) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        try {
            return TablePreviewResponse.from(inspector.preview(
                    dataSource.getType().name(),
                    config,
                    resolvedTable(dialect, config, catalog, schema, table),
                    limit
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public JdbcQueryInspectionResponse inspectQuery(UUID id, String sql) {
        DataSource dataSource = requireDataSource(id);
        if (!dataSource.isEnabled()
                || !dataSource.getPurposes().contains(cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose.SOURCE)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "查询输入数据源不存在、未启用或不具有 SOURCE 用途");
        }
        if (dataSource.getType() != DataSourceType.POSTGRESQL
                && dataSource.getType() != DataSourceType.MYSQL) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "JDBC 查询输入只支持 PostgreSQL 和 MySQL");
        }
        if (sql == null || sql.isBlank() || sql.length() > 100_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 长度必须为 1 到 100000 个字符");
        }
        var query = parseReadOnlyQuery(sql);
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        QueryInspection inspection;
        try {
            inspection = queryInspector.inspect(
                    dataSource.getType().name(),
                    dataSource.getConnection().toJdbcConnectionConfig(),
                    query,
                    Duration.ofSeconds(30)
            );
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
        Set<String> names = new HashSet<>();
        List<CanvasColumnSchema> columns = inspection.columns().stream().map(column -> {
            if (!names.add(column.label())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "查询结果字段名重复：" + column.label());
            }
            var mapping = dialect.mapToPlatformType(column.jdbcTypeDescriptor());
            if (!mapping.acceptable() || mapping.definition() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "查询结果字段无法无损映射为平台类型：" + column.label());
            }
            var type = mapping.definition();
            if (type.type() == PlatformDataType.GEOMETRY) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "JDBC_QUERY_INPUT 第一版不支持 Geometry 查询字段：" + column.label());
            }
            return new CanvasColumnSchema(
                    column.label(),
                    type.type(),
                    type.length(),
                    type.precision(),
                    type.scale(),
                    column.nullable(),
                    null,
                    false,
                    false,
                    null,
                    null
            );
        }).toList();
        return new JdbcQueryInspectionResponse(ReadOnlyQueryFingerprint.sha256(query), columns);
    }

    private static cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery parseReadOnlyQuery(String sql) {
        try {
            return ReadOnlySelectQueryParser.parse(sql);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public List<KafkaTopicResponse> listKafkaTopics(UUID id, String keyword, boolean includeInternal) {
        DataSource dataSource = requireDataSource(id);
        if (dataSource.getType() != DataSourceType.KAFKA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该数据源不是 Kafka");
        }
        if (!dataSource.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kafka 数据源已停用");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        try (Admin admin = Admin.create(adminProperties(kafkaSettings(dataSource)))) {
            List<TopicListing> listings = admin.listTopics(new ListTopicsOptions().listInternal(includeInternal))
                    .listings()
                    .get(KAFKA_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .stream()
                    .filter(listing -> normalizedKeyword.isEmpty()
                            || listing.name().toLowerCase(java.util.Locale.ROOT)
                            .contains(normalizedKeyword.toLowerCase(java.util.Locale.ROOT)))
                    .sorted(java.util.Comparator.comparing(TopicListing::name))
                    .limit(200)
                    .toList();
            Map<String, TopicDescription> descriptions = describeKafkaTopics(admin, listings, id);
            return listings.stream()
                    .map(listing -> kafkaTopicResponse(listing, descriptions.get(listing.name())))
                    .toList();
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Kafka Topic 查询失败", exception);
        }
    }

    public List<TdEngineTmqTopicResponse> listTdEngineTmqTopics(UUID id, String keyword) {
        DataSource dataSource = requireTdEngineWebSocket(id);
        try {
            return inspector.listTdEngineTmqTopics(
                            dataSource.getType().name(),
                            dataSource.getConnection().toJdbcConnectionConfig(),
                            keyword
                    ).stream()
                    .limit(500)
                    .map(TdEngineTmqTopicResponse::from)
                    .toList();
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TdEngineTmqTopicDetailResponse readTdEngineTmqTopic(UUID id, String topicName) {
        if (topicName == null || topicName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic 名称不能为空");
        }
        DataSource dataSource = requireTdEngineWebSocket(id);
        try {
            var topic = inspector.listTdEngineTmqTopics(
                            dataSource.getType().name(),
                            dataSource.getConnection().toJdbcConnectionConfig(),
                            topicName.trim()
                    ).stream()
                    .filter(candidate -> candidate.topicName().equals(topicName.trim()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TMQ Topic 不存在"));
            return TdEngineTmqTopicDetailResponse.from(topic, registry.require(dataSource.getType().name()));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    private Map<String, TopicDescription> describeKafkaTopics(
            Admin admin,
            List<TopicListing> listings,
            UUID dataSourceId
    ) {
        if (listings.isEmpty()) return Map.of();
        try {
            return admin.describeTopics(listings.stream().map(TopicListing::name).toList())
                    .allTopicNames()
                    .get(KAFKA_API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn(
                    "Kafka Topic metadata unavailable: dataSourceId={}, topicCount={}",
                    dataSourceId,
                    listings.size(),
                    exception
            );
            return Map.of();
        }
    }

    private static KafkaTopicResponse kafkaTopicResponse(
            TopicListing listing,
            TopicDescription description
    ) {
        String topicId = listing.topicId() == null || Uuid.ZERO_UUID.equals(listing.topicId())
                ? null : listing.topicId().toString();
        if (description == null) {
            return new KafkaTopicResponse(
                    listing.name(), topicId, listing.isInternal(), false,
                    null, null, null, null, null
            );
        }
        List<TopicPartitionInfo> partitions = description.partitions();
        int minimumReplicationFactor = partitions.stream()
                .mapToInt(partition -> partition.replicas().size())
                .min()
                .orElse(0);
        int maximumReplicationFactor = partitions.stream()
                .mapToInt(partition -> partition.replicas().size())
                .max()
                .orElse(0);
        int underReplicatedPartitionCount = (int) partitions.stream()
                .filter(partition -> partition.isr().size() < partition.replicas().size())
                .count();
        int unavailableLeaderPartitionCount = (int) partitions.stream()
                .filter(partition -> partition.leader() == null || partition.leader().id() < 0)
                .count();
        return new KafkaTopicResponse(
                listing.name(), topicId, listing.isInternal(), true,
                partitions.size(), minimumReplicationFactor, maximumReplicationFactor,
                underReplicatedPartitionCount, unavailableLeaderPartitionCount
        );
    }

    public void requireKafkaTopics(UUID id, Set<String> topics) {
        if (topics == null || topics.isEmpty()) return;
        DataSource dataSource = requireDataSource(id);
        if (dataSource.getType() != DataSourceType.KAFKA || !dataSource.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kafka 数据源不存在或已停用");
        }
        try (Admin admin = Admin.create(adminProperties(kafkaSettings(dataSource)))) {
            Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions =
                    admin.describeTopics(topics).allTopicNames()
                            .get(KAFKA_API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (String topic : topics) {
                if (!descriptions.containsKey(topic)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Kafka Topic 不存在：" + topic);
                }
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Kafka Topic 预检失败", exception);
        }
    }

    private DataSource requireDataSource(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }

    private DataSource requireTdEngineWebSocket(UUID id) {
        DataSource dataSource = requireDataSource(id);
        if (dataSource.getType() != DataSourceType.TDENGINE_WEBSOCKET) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "只有 TDengine WebSocket JDBC 数据源支持 TMQ Topic");
        }
        return dataSource;
    }

    private ConnectionTestResponse testConnection(
            String context,
            String databaseType,
            JdbcConnectionConfig config
    ) {
        long startedAt = System.nanoTime();
        try {
            return ConnectionTestResponse.from(inspector.test(databaseType, config));
        } catch (DatabaseAccessException exception) {
            long elapsedMs = elapsedMs(startedAt);
            Throwable failure = exception.getCause() == null ? exception : exception.getCause();
            ConnectionTestDiagnosticResponse diagnostic = ConnectionTestDiagnosticFactory.create(
                    failure,
                    config.password()
            );
            LOGGER.warn(
                    "JDBC connection test failed: context={}, databaseType={}, target={}:{}/{}, "
                            + "code={}, sqlState={}, vendorCode={}, elapsedMs={}\n{}",
                    context,
                    databaseType,
                    config.host(),
                    config.port(),
                    config.databaseName(),
                    exception.code(),
                    diagnostic.sqlState(),
                    diagnostic.vendorCode(),
                    elapsedMs,
                    ConnectionTestDiagnosticFactory.sanitizedStackTrace(failure, config.password())
            );
            return ConnectionTestResponse.failed(exception.code(), exception.getMessage(), elapsedMs, diagnostic);
        }
    }

    private ConnectionTestResponse testKafka(String context, KafkaSettings settings) {
        long startedAt = System.nanoTime();
        try (Admin admin = Admin.create(adminProperties(settings))) {
            String clusterId = admin.describeCluster().clusterId()
                    .get(KAFKA_API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (clusterId == null || clusterId.isBlank()) {
                throw new IllegalStateException("Kafka Cluster ID 不可用");
            }
            return new ConnectionTestResponse(
                    true,
                    "OK",
                    "Kafka 连接成功",
                    elapsedMs(startedAt),
                    "Apache Kafka",
                    clusterId,
                    "Kafka AdminClient",
                    null
            );
        } catch (Exception exception) {
            ConnectionTestDiagnosticResponse diagnostic = ConnectionTestDiagnosticFactory.create(
                    exception, settings.password());
            LOGGER.warn(
                    "Kafka connection test failed: context={}, code=KAFKA_CONNECTION_FAILED, elapsedMs={}",
                    context, elapsedMs(startedAt));
            return ConnectionTestResponse.failed(
                    "KAFKA_CONNECTION_FAILED",
                    "Kafka 连接失败",
                    elapsedMs(startedAt),
                    diagnostic
            );
        }
    }

    private ConnectionTestResponse testS3(String context, S3Settings settings) {
        long startedAt = System.nanoTime();
        String probeKey = joinKey(
                settings.rootPrefix(),
                ".datascalpel-connection-test/" + UUID.randomUUID()
        );
        boolean objectCreated = false;
        try (S3Client client = s3Client(settings)) {
            client.putObject(
                    PutObjectRequest.builder().bucket(settings.bucket()).key(probeKey).build(),
                    RequestBody.fromBytes("datascalpel".getBytes(StandardCharsets.UTF_8))
            );
            objectCreated = true;
            boolean listed = client.listObjectsV2(ListObjectsV2Request.builder()
                            .bucket(settings.bucket())
                            .prefix(probeKey)
                            .maxKeys(1)
                            .build())
                    .contents().stream()
                    .anyMatch(object -> probeKey.equals(object.key()));
            if (!listed) {
                throw new IllegalStateException("S3 测试对象写入后不可见");
            }
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(settings.bucket()).key(probeKey).build());
            objectCreated = false;
            return new ConnectionTestResponse(
                    true,
                    "OK",
                    "S3 连接成功",
                    elapsedMs(startedAt),
                    "S3 Compatible",
                    settings.bucket(),
                    "AWS SDK v2",
                    null
            );
        } catch (Exception exception) {
            ConnectionTestDiagnosticResponse diagnostic = ConnectionTestDiagnosticFactory.create(
                    exception, settings.accessKey(), settings.secretKey());
            LOGGER.warn(
                    "S3 connection test failed: context={}, endpoint={}, bucket={}, code=S3_CONNECTION_FAILED, elapsedMs={}",
                    context, settings.endpoint(), settings.bucket(), elapsedMs(startedAt));
            return ConnectionTestResponse.failed(
                    "S3_CONNECTION_FAILED",
                    "S3 连接或读写权限测试失败",
                    elapsedMs(startedAt),
                    diagnostic
            );
        } finally {
            if (objectCreated) {
                try (S3Client client = s3Client(settings)) {
                    client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(settings.bucket()).key(probeKey).build());
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn(
                            "S3 connection test cleanup failed: context={}, endpoint={}, bucket={}",
                            context, settings.endpoint(), settings.bucket());
                }
            }
        }
    }

    private static S3Client s3Client(S3Settings settings) {
        return S3Client.builder()
                .endpointOverride(URI.create(settings.endpoint()))
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        settings.accessKey(), settings.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(settings.pathStyleAccess())
                        .build())
                .build();
    }

    private static S3Settings s3Settings(S3DataSourceConnectionRequest request) {
        if (request.secretKey() == null || request.secretKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "S3 SecretKey 不能为空");
        }
        return new S3Settings(
                request.endpoint().trim(),
                defaultS3Region(request.region()),
                request.bucket().trim(),
                normalizeS3Prefix(request.rootPrefix()),
                request.pathStyleAccess() == null || request.pathStyleAccess(),
                request.accessKey().trim(),
                request.secretKey()
        );
    }

    private static S3Settings s3Settings(DataSource dataSource) {
        Map<String, String> options = dataSource.getConnection().getOptions();
        return new S3Settings(
                dataSource.getConnection().getEndpoint(),
                defaultS3Region(options.get("region")),
                dataSource.getConnection().getTarget(),
                normalizeS3Prefix(dataSource.getConnection().getNamespace()),
                Boolean.parseBoolean(options.getOrDefault("pathStyleAccess", "true")),
                dataSource.getConnection().getPrincipal(),
                dataSource.getConnection().secretValue()
        );
    }

    private static String defaultS3Region(String region) {
        return region == null || region.isBlank() ? "us-east-1" : region.trim();
    }

    private static String normalizeS3Prefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return null;
        return prefix.trim().replaceFirst("^/+", "").replaceFirst("/+$", "");
    }

    private static String joinKey(String prefix, String suffix) {
        return prefix == null || prefix.isBlank() ? suffix : prefix + "/" + suffix;
    }

    public HttpApiContracts.RuntimeConnection runtimeConnection(DataSource dataSource) {
        if (dataSource.getType().connectionKind() != DataSourceConnectionKind.HTTP_API) {
            throw unsupportedRuntime(dataSource.getType());
        }
        return runtimeConnection(dataSource.getConnection());
    }

    private HttpApiContracts.RuntimeConnection runtimeConnection(
            cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection connection
    ) {
        HttpApiContracts.ConnectionConfiguration configuration = HttpApiConfigurationCodec.readConnectionConfiguration(
                connection.apiConfigurationValue());
        HttpApiContracts.CredentialBundle credentials = HttpApiConfigurationCodec.readCredentials(
                credentialCipher.decrypt(connection.apiCredentialsCiphertextValue()));
        return new HttpApiContracts.RuntimeConnection(configuration, credentials);
    }

    private ConnectionTestResponse testHttpApi(String context, HttpApiContracts.RuntimeConnection connection) {
        ConnectionProbeResult result = httpApiConnectors.require(HttpApiContracts.GENERIC_CONNECTOR)
                .testConnection(connection);
        if (result.success()) {
            return new ConnectionTestResponse(
                    true, result.code(), result.message(), result.elapsedMs(),
                    "HTTP API", result.httpStatus() == null ? null : "HTTP " + result.httpStatus(),
                    "Java HttpClient", null
            );
        }
        String[] sensitiveValues = ConnectionTestDiagnosticFactory.httpSensitiveValues(connection);
        Throwable cause = result.failure() == null ? null : result.failure().cause();
        ConnectionTestDiagnosticResponse causeDiagnostic = cause == null
                ? null : ConnectionTestDiagnosticFactory.create(cause, sensitiveValues);
        ConnectionTestDiagnosticResponse diagnostic = new ConnectionTestDiagnosticResponse(
                cause == null ? "HTTP_API" : cause.getClass().getName(),
                ConnectionTestDiagnosticFactory.sanitizedText(
                        result.failure() == null ? result.message() : result.failure().message(), sensitiveValues),
                null,
                null,
                result.httpStatus(),
                ConnectionTestDiagnosticFactory.sanitizedText(
                        result.failure() == null ? null : result.failure().responsePreview(), sensitiveValues),
                causeDiagnostic == null ? List.of() : causeDiagnostic.causes()
        );
        if (cause == null) {
            LOGGER.warn(
                    "HTTP API connection test failed: context={}, baseUrl={}, code={}, httpStatus={}, elapsedMs={}",
                    context, connection.configuration().baseUrl(), result.code(), result.httpStatus(), result.elapsedMs());
        } else {
            LOGGER.warn(
                    "HTTP API connection test failed: context={}, baseUrl={}, code={}, httpStatus={}, elapsedMs={}\n{}",
                    context, connection.configuration().baseUrl(), result.code(), result.httpStatus(), result.elapsedMs(),
                    ConnectionTestDiagnosticFactory.sanitizedStackTrace(cause, sensitiveValues));
        }
        return ConnectionTestResponse.failed(result.code(), result.message(), result.elapsedMs(), diagnostic);
    }

    private ConnectionTestResponse testSpatialService(
            String context,
            DataSourceType type,
            HttpApiContracts.RuntimeConnection connection
    ) {
        long startedAt = System.nanoTime();
        String[] sensitiveValues = ConnectionTestDiagnosticFactory.httpSensitiveValues(connection);
        try {
            SpatialServiceClient.SpatialProbe probe = spatialServiceClient.probe(spatialProtocol(type), connection);
            return new ConnectionTestResponse(
                    true, "CONNECTION_SUCCESS", "连接成功", elapsedMs(startedAt),
                    probe.product(), probe.version(), "Java HttpClient", null
            );
        } catch (RuntimeException exception) {
            String code = "SPATIAL_SERVICE_CONNECTION_FAILED";
            String message = exception.getMessage();
            if (exception instanceof ResponseStatusException responseStatusException) {
                message = responseStatusException.getReason();
                Object problemCode = responseStatusException.getBody().getProperties() == null ? null
                        : responseStatusException.getBody().getProperties().get("code");
                if (problemCode instanceof String value && !value.isBlank()) {
                    code = value;
                }
            }
            LOGGER.warn(
                    "Spatial service connection test failed: context={}, type={}, baseUrl={}, code={}, elapsedMs={}\\n{}",
                    context, type, connection.configuration().baseUrl(), code, elapsedMs(startedAt),
                    ConnectionTestDiagnosticFactory.sanitizedStackTrace(exception, sensitiveValues));
            return ConnectionTestResponse.failed(
                    code,
                    ConnectionTestDiagnosticFactory.sanitizedText(message, sensitiveValues),
                    elapsedMs(startedAt),
                    ConnectionTestDiagnosticFactory.create(exception, sensitiveValues)
            );
        }
    }

    private static boolean isSpatial(DataSourceType type) {
        return type == DataSourceType.ARCGIS_REST || type == DataSourceType.WFS;
    }

    private static cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol spatialProtocol(
            DataSourceType type
    ) {
        return type == DataSourceType.ARCGIS_REST
                ? cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol.ARCGIS_REST
                : cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol.WFS;
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static JdbcConnectionConfig toConfig(JdbcDataSourceConnectionRequest connection) {
        return new JdbcConnectionConfig(
                connection.host(),
                connection.port(),
                connection.databaseName(),
                connection.schemaName(),
                connection.username(),
                connection.password(),
                connection.options()
        );
    }

    private static JdbcDataSourceConnectionRequest requireJdbc(
            DataSourceType type,
            DataSourceConnectionRequest connection
    ) {
        if (type.connectionKind() != DataSourceConnectionKind.JDBC || !(connection instanceof JdbcDataSourceConnectionRequest jdbc)) {
            throw unsupportedRuntime(type);
        }
        return jdbc;
    }

    private static void validateKafka(KafkaDataSourceConnectionRequest request) {
        String protocol = request.securityProtocol() == null || request.securityProtocol().isBlank()
                ? "PLAINTEXT" : request.securityProtocol();
        boolean sasl = "SASL_PLAINTEXT".equals(protocol) || "SASL_SSL".equals(protocol);
        if (request.bootstrapServers() == null || request.bootstrapServers().isBlank()
                || request.bootstrapServers().contains("\r")
                || request.bootstrapServers().contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kafka Bootstrap Servers 不合法");
        }
        if (sasl) {
            if (!Set.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512")
                    .contains(request.saslMechanism())
                    || request.username() == null || request.username().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "SASL Kafka 必须配置受支持的机制和用户名");
            }
        } else if (request.saslMechanism() != null && !request.saslMechanism().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "非 SASL Kafka 不能配置 SASL 机制");
        }
    }

    private static KafkaSettings kafkaSettings(KafkaDataSourceConnectionRequest request) {
        return new KafkaSettings(
                request.bootstrapServers().trim(),
                request.securityProtocol() == null || request.securityProtocol().isBlank()
                        ? "PLAINTEXT" : request.securityProtocol(),
                request.saslMechanism(),
                request.username(),
                request.password()
        );
    }

    private static KafkaSettings kafkaSettings(DataSource dataSource) {
        return new KafkaSettings(
                dataSource.getConnection().getEndpoint(),
                dataSource.getConnection().getOptions()
                        .getOrDefault("securityProtocol", "PLAINTEXT"),
                dataSource.getConnection().getOptions().get("saslMechanism"),
                dataSource.getConnection().getPrincipal(),
                dataSource.getConnection().secretValue()
        );
    }

    private static Properties adminProperties(KafkaSettings settings) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "datascalpel-admin-" + UUID.randomUUID());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, KAFKA_REQUEST_TIMEOUT_MS);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, KAFKA_API_TIMEOUT_MS);
        properties.put(
                CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG,
                KAFKA_CONNECTION_SETUP_TIMEOUT_MS
        );
        properties.put(
                CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG,
                KAFKA_CONNECTION_SETUP_TIMEOUT_MAX_MS
        );
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, settings.securityProtocol());
        if (settings.saslMechanism() != null && !settings.saslMechanism().isBlank()) {
            String loginModule = "PLAIN".equals(settings.saslMechanism())
                    ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                    : "org.apache.kafka.common.security.scram.ScramLoginModule";
            properties.put(SaslConfigs.SASL_MECHANISM, settings.saslMechanism());
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, loginModule + " required username=\""
                    + jaas(settings.username()) + "\" password=\""
                    + jaas(settings.password()) + "\";");
        }
        return properties;
    }

    private static String jaas(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void requireJdbc(DataSource dataSource) {
        if (!dataSource.getType().isJdbc()) {
            throw unsupportedRuntime(dataSource.getType());
        }
    }

    private static ResponseStatusException unsupportedRuntime(DataSourceType type) {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, type.displayName() + "连接器尚未实现运行时操作");
    }

    private static TableIdentifier resolvedTable(
            DatabaseDialect dialect,
            JdbcConnectionConfig config,
            String catalog,
            String schema,
            String table
    ) {
        return new TableIdentifier(
                dialect.resolveCatalog(config, catalog),
                dialect.resolveSchema(config, schema),
                table
        );
    }

    private static ResponseStatusException remoteAccessException(DatabaseAccessException exception) {
        HttpStatus status = "TABLE_NOT_FOUND".equals(exception.code()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    private record KafkaSettings(
            String bootstrapServers,
            String securityProtocol,
            String saslMechanism,
            String username,
            String password
    ) {
    }

    private record S3Settings(
            String endpoint,
            String region,
            String bucket,
            String rootPrefix,
            boolean pathStyleAccess,
            String accessKey,
            String secretKey
    ) {
    }
}
