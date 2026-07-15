package cn.superhuang.data.scalpel.engine.datasource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.engine.crypto.EngineSnapshotCipher;
import cn.superhuang.data.scalpel.engine.deployment.EngineDeploymentRecordStatus;
import cn.superhuang.data.scalpel.engine.deployment.EngineDeploymentRepository;
import cn.superhuang.data.scalpel.engine.query.DataSourcePoolRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, encrypted Engine-local JDBC data source registry. */
@Service
public class EngineDataSourceStore {

    private final EngineDataSourceRepository repository;
    private final EngineDeploymentRepository deploymentRepository;
    private final EngineProperties properties;
    private final EngineSnapshotCipher cipher;
    private final ObjectMapper objectMapper;
    private final DataSourcePoolRegistry poolRegistry;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, JdbcDataSourceSnapshot> snapshots = new ConcurrentHashMap<>();

    public EngineDataSourceStore(
            EngineDataSourceRepository repository,
            EngineDeploymentRepository deploymentRepository,
            EngineProperties properties,
            EngineSnapshotCipher cipher,
            ObjectMapper objectMapper,
            DataSourcePoolRegistry poolRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.properties = properties;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.poolRegistry = poolRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public EngineDataSourceRegistrationResponse register(EngineDataSourceRegistrationRequest request) {
        if (!request.dataSourceId().equals(request.dataSource().dataSourceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源标识与快照不一致");
        }
        JdbcDataSourceSnapshot snapshot = request.dataSource();
        String snapshotJson = write(snapshot);
        String snapshotDigest = digest(snapshotJson);
        RegistrationCheck check = requireTransactionResult(transactionTemplate.execute(
                status -> checkRegistration(request, snapshotDigest)
        ));
        if (!check.writeRequired()) {
            return check.response();
        }

        try {
            poolRegistry.test(snapshot);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接注册的数据源", exception);
        }
        RegistrationResult result = requireTransactionResult(transactionTemplate.execute(
                status -> applyRegistration(request, snapshotJson, snapshotDigest)
        ));
        if (result.applied()) {
            snapshots.put(request.dataSourceId(), snapshot);
            poolRegistry.evict(request.dataSourceId());
        }
        return result.response();
    }

    private RegistrationCheck checkRegistration(
            EngineDataSourceRegistrationRequest request,
            String snapshotDigest
    ) {
        EngineDataSource existing = repository.findByEngineCodeAndDataSourceId(properties.code(), request.dataSourceId())
                .orElse(null);
        if (existing != null && existing.getRevision() > request.revision()) {
            return new RegistrationCheck(false, response(existing, "已存在更新版本的数据源注册"));
        }
        if (existing != null && existing.getRevision() == request.revision()) {
            if (!existing.getSnapshotDigest().equals(snapshotDigest)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一数据源版本的快照不一致");
            }
            return new RegistrationCheck(false, response(existing, "数据源注册已存在"));
        }
        return new RegistrationCheck(true, null);
    }

    private RegistrationResult applyRegistration(
            EngineDataSourceRegistrationRequest request,
            String snapshotJson,
            String snapshotDigest
    ) {
        RegistrationCheck current = checkRegistration(request, snapshotDigest);
        if (!current.writeRequired()) {
            return new RegistrationResult(current.response(), false);
        }
        EngineDataSource existing = repository.findByEngineCodeAndDataSourceId(properties.code(), request.dataSourceId())
                .orElse(null);
        if (existing == null) {
            existing = EngineDataSource.create(
                    properties.code(), request.dataSourceId(), request.revision(), request.dataSource().databaseType(), snapshotDigest,
                    cipher.encrypt(snapshotJson)
            );
        } else {
            existing.apply(
                    request.revision(), request.dataSource().databaseType(), snapshotDigest, cipher.encrypt(snapshotJson)
            );
        }
        repository.saveAndFlush(existing);
        return new RegistrationResult(response(existing, "数据源已注册"), true);
    }

    public EngineDataSourceTestResponse test(UUID dataSourceId) {
        TestPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            EngineDataSource dataSource = repository.findByEngineCodeAndDataSourceId(properties.code(), dataSourceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Engine 数据源不存在"));
            return new TestPreparation(dataSource.getRevision(), snapshot(dataSource));
        }));
        try {
            poolRegistry.test(preparation.snapshot());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "数据源连接测试失败", exception);
        }
        return new EngineDataSourceTestResponse(
                properties.code(), dataSourceId, preparation.revision(), preparation.snapshot().databaseType()
        );
    }

    public EngineDataSourceRegistrationResponse remove(EngineDataSourceRemovalRequest request) {
        RemovalResult result = requireTransactionResult(
                transactionTemplate.execute(status -> removeRegistration(request))
        );
        if (result.removed()) {
            snapshots.remove(request.dataSourceId());
            poolRegistry.evict(request.dataSourceId());
        }
        return result.response();
    }

    private RemovalResult removeRegistration(EngineDataSourceRemovalRequest request) {
        if (deploymentRepository.existsByEngineCodeAndDataSourceIdAndStatus(
                properties.code(), request.dataSourceId(), EngineDeploymentRecordStatus.DEPLOYED
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有已部署服务正在使用该数据源");
        }
        EngineDataSource existing = repository.findByEngineCodeAndDataSourceId(properties.code(), request.dataSourceId())
                .orElse(null);
        if (existing == null) {
            return new RemovalResult(new EngineDataSourceRegistrationResponse(
                    properties.code(), request.dataSourceId(), request.revision(), EngineDataSourceStatus.REMOVED, "数据源不存在"
            ), false);
        }
        if (existing.getRevision() > request.revision()) {
            return new RemovalResult(new EngineDataSourceRegistrationResponse(
                    properties.code(), request.dataSourceId(), existing.getRevision(), EngineDataSourceStatus.READY,
                    "已存在更新版本的数据源注册"
            ), false);
        }
        repository.delete(existing);
        repository.flush();
        return new RemovalResult(new EngineDataSourceRegistrationResponse(
                properties.code(), request.dataSourceId(), request.revision(), EngineDataSourceStatus.REMOVED, "数据源已移除"
        ), true);
    }

    @Transactional(readOnly = true)
    public void restore() {
        snapshots.clear();
        repository.findAllByEngineCode(properties.code()).forEach(dataSource ->
                snapshots.put(dataSource.getDataSourceId(), snapshot(dataSource))
        );
    }

    public JdbcDataSourceSnapshot requireSnapshot(UUID dataSourceId) {
        JdbcDataSourceSnapshot snapshot = snapshots.get(dataSourceId);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引用的数据源尚未注册到 Engine");
        }
        return snapshot;
    }

    private EngineDataSourceRegistrationResponse response(EngineDataSource dataSource, String message) {
        return new EngineDataSourceRegistrationResponse(
                properties.code(), dataSource.getDataSourceId(), dataSource.getRevision(), EngineDataSourceStatus.READY, message
        );
    }

    private JdbcDataSourceSnapshot snapshot(EngineDataSource dataSource) {
        return read(cipher.decrypt(dataSource.getEncryptedSnapshotJson()), JdbcDataSourceSnapshot.class);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Engine 数据源快照", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法读取 Engine 数据源快照", exception);
        }
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回 Engine 数据源处理结果");
        }
        return value;
    }

    private record RegistrationCheck(boolean writeRequired, EngineDataSourceRegistrationResponse response) {
    }

    private record RegistrationResult(EngineDataSourceRegistrationResponse response, boolean applied) {
    }

    private record TestPreparation(long revision, JdbcDataSourceSnapshot snapshot) {
    }

    private record RemovalResult(EngineDataSourceRegistrationResponse response, boolean removed) {
    }
}
