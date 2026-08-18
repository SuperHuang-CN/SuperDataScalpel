package cn.superhuang.data.scalpel.business.asset.service;

import cn.superhuang.data.scalpel.business.asset.domain.Asset;
import cn.superhuang.data.scalpel.business.asset.domain.AssetStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.repository.AssetRepository;
import cn.superhuang.data.scalpel.business.asset.web.request.RegisterAssetsRequest;
import cn.superhuang.data.scalpel.business.asset.web.request.UpdateAssetRequest;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetBatchOperationResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetCandidateResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssetManagementService {

    private final AssetRepository repository;
    private final AssetSourceService sourceService;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    public AssetManagementService(
            AssetRepository repository,
            AssetSourceService sourceService,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.sourceService = sourceService;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }

    public PageResponse<AssetResponse> search(SearchRequest request) {
        return required(readTransactionTemplate.execute(status -> {
            var page = searchEngine.search(request, Asset.class, repository);
            return new PageResponse<>(
                    page.getContent().stream().map(this::response).toList(),
                    page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
            );
        }));
    }

    public AssetResponse get(UUID id) {
        return required(readTransactionTemplate.execute(status -> response(requireAsset(id))));
    }

    public PageResponse<AssetCandidateResponse> candidates(AssetType assetType, String keyword, Integer page, Integer size) {
        if (assetType == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资产类型不能为空");
        return sourceService.candidates(assetType, keyword, page, size);
    }

    public List<AssetResponse> register(RegisterAssetsRequest request) {
        List<UUID> resourceIds = new ArrayList<>(new LinkedHashSet<>(request.resourceIds()));
        List<AssetSourceSnapshot> snapshots = resourceIds.stream()
                .map(resourceId -> sourceService.read(request.assetType(), resourceId))
                .toList();
        snapshots.stream().filter(snapshot -> !snapshot.available()).findFirst().ifPresent(snapshot -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, snapshot.unavailableReason());
        });
        try {
            return required(transactionTemplate.execute(status -> {
                for (UUID resourceId : resourceIds) {
                    if (repository.existsByAssetTypeAndResourceId(request.assetType(), resourceId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "资源已经登记为资产");
                    }
                }
                Instant now = Instant.now();
                List<Asset> assets = snapshots.stream().map(snapshot -> {
                    Asset asset = Asset.register(snapshot.assetType(), snapshot.resourceId());
                    applySnapshot(asset, snapshot, now);
                    return asset;
                }).toList();
                return repository.saveAllAndFlush(assets).stream().map(this::response).toList();
            }));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资源已经登记为资产", exception);
        }
    }

    public AssetResponse update(UUID id, UpdateAssetRequest request) {
        List<String> tags = normalizeTags(request.tags());
        String tagsJson = writeJson(tags, "无法保存资产标签");
        return required(transactionTemplate.execute(status -> {
            Asset asset = requireAsset(id);
            directoryService.validateAssignment(DirectoryScope.ASSET, request.directoryId());
            asset.updatePortal(
                    request.directoryId(), request.portalName(), request.portalSummary(), tagsJson,
                    request.ownerName(), request.updateFrequency(), request.sensitivityLevel(), request.featured()
            );
            return response(repository.saveAndFlush(asset));
        }));
    }

    public AssetResponse publish(UUID id) {
        AssetReference reference = reference(id);
        AssetSourceSnapshot snapshot = readSource(reference);
        if (!snapshot.available()) {
            recordSourceState(id, AssetSyncStatus.SOURCE_UNAVAILABLE, snapshot.unavailableReason());
            throw new ResponseStatusException(HttpStatus.CONFLICT, snapshot.unavailableReason());
        }
        return required(transactionTemplate.execute(status -> {
            Asset asset = requireAsset(id);
            applySnapshot(asset, snapshot, Instant.now());
            validatePublishable(asset);
            try {
                asset.publish(Instant.now());
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
            }
            return response(repository.saveAndFlush(asset));
        }));
    }

    public AssetResponse offline(UUID id) {
        return required(transactionTemplate.execute(status -> {
            Asset asset = requireAsset(id);
            try {
                asset.offline(Instant.now());
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
            }
            return response(repository.saveAndFlush(asset));
        }));
    }

    public void delete(UUID id) {
        transactionTemplate.executeWithoutResult(status -> {
            Asset asset = requireAsset(id);
            if (asset.getStatus() == AssetStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布资产请先下线");
            }
            repository.delete(asset);
        });
    }

    public AssetResponse check(UUID id) {
        AssetReference reference = reference(id);
        try {
            AssetSourceSnapshot snapshot = sourceService.read(reference.assetType(), reference.resourceId());
            AssetSyncStatus status = snapshot.available()
                    ? (snapshot.fingerprint().equals(reference.fingerprint()) ? AssetSyncStatus.IN_SYNC : AssetSyncStatus.OUTDATED)
                    : AssetSyncStatus.SOURCE_UNAVAILABLE;
            return recordSourceState(id, status, snapshot.available() ? null : snapshot.unavailableReason());
        } catch (ResponseStatusException exception) {
            return recordReadFailure(id, exception);
        } catch (RuntimeException exception) {
            return recordSourceState(id, AssetSyncStatus.FAILED, safeMessage(exception));
        }
    }

    public AssetResponse sync(UUID id) {
        AssetReference reference = reference(id);
        try {
            AssetSourceSnapshot snapshot = sourceService.read(reference.assetType(), reference.resourceId());
            if (!snapshot.available()) {
                return recordSourceState(id, AssetSyncStatus.SOURCE_UNAVAILABLE, snapshot.unavailableReason());
            }
            return required(transactionTemplate.execute(status -> {
                Asset asset = requireAsset(id);
                applySnapshot(asset, snapshot, Instant.now());
                return response(repository.saveAndFlush(asset));
            }));
        } catch (ResponseStatusException exception) {
            return recordReadFailure(id, exception);
        } catch (RuntimeException exception) {
            return recordSourceState(id, AssetSyncStatus.FAILED, safeMessage(exception));
        }
    }

    public AssetBatchOperationResponse checkAll() {
        return batch(false);
    }

    public AssetBatchOperationResponse syncAll() {
        return batch(true);
    }

    private AssetBatchOperationResponse batch(boolean synchronize) {
        List<AssetReference> references = required(readTransactionTemplate.execute(status -> repository.findAll().stream()
                .map(asset -> new AssetReference(
                        asset.getId(), asset.effectiveName(), asset.getAssetType(), asset.getResourceId(), asset.getSourceFingerprint()
                )).toList()));
        int success = 0;
        int outdated = 0;
        int unavailable = 0;
        int missing = 0;
        int failed = 0;
        List<AssetBatchOperationResponse.Failure> failures = new ArrayList<>();
        for (AssetReference reference : references) {
            try {
                AssetResponse result = synchronize ? sync(reference.assetId()) : check(reference.assetId());
                switch (result.syncStatus()) {
                    case IN_SYNC -> success++;
                    case OUTDATED -> outdated++;
                    case SOURCE_UNAVAILABLE -> unavailable++;
                    case SOURCE_MISSING -> missing++;
                    case FAILED -> {
                        failed++;
                        failures.add(new AssetBatchOperationResponse.Failure(
                                reference.assetId(), reference.name(), result.syncError()
                        ));
                    }
                }
            } catch (RuntimeException exception) {
                failed++;
                failures.add(new AssetBatchOperationResponse.Failure(
                        reference.assetId(), reference.name(), safeMessage(exception)
                ));
            }
        }
        return new AssetBatchOperationResponse(
                references.size(), success, outdated, unavailable, missing, failed, failures
        );
    }

    private AssetReference reference(UUID id) {
        return required(readTransactionTemplate.execute(status -> {
            Asset asset = requireAsset(id);
            return new AssetReference(
                    asset.getId(), asset.effectiveName(), asset.getAssetType(), asset.getResourceId(), asset.getSourceFingerprint()
            );
        }));
    }

    private AssetSourceSnapshot readSource(AssetReference reference) {
        try {
            return sourceService.read(reference.assetType(), reference.resourceId());
        } catch (ResponseStatusException exception) {
            recordReadFailure(reference.assetId(), exception);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资产来源不可用：" + safeMessage(exception), exception);
        } catch (RuntimeException exception) {
            recordSourceState(reference.assetId(), AssetSyncStatus.FAILED, safeMessage(exception));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "读取资产来源失败", exception);
        }
    }

    private AssetResponse recordReadFailure(UUID id, ResponseStatusException exception) {
        AssetSyncStatus status = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                ? AssetSyncStatus.SOURCE_MISSING
                : AssetSyncStatus.FAILED;
        return recordSourceState(id, status, safeMessage(exception));
    }

    private AssetResponse recordSourceState(UUID id, AssetSyncStatus status, String error) {
        return required(transactionTemplate.execute(transactionStatus -> {
            Asset asset = requireAsset(id);
            asset.recordCheck(status, error, Instant.now());
            return response(repository.saveAndFlush(asset));
        }));
    }

    private void applySnapshot(Asset asset, AssetSourceSnapshot snapshot, Instant now) {
        asset.synchronizeSource(
                snapshot.name(), snapshot.code(), snapshot.description(), snapshot.sourceStatus(), snapshot.sourceUpdatedAt(),
                snapshot.json(), snapshot.fingerprint(), now
        );
    }

    private void validatePublishable(Asset asset) {
        directoryService.validateAssignment(DirectoryScope.ASSET, asset.getDirectoryId());
        if (asset.getDirectoryId() == null) throw conflict("请选择业务领域");
        if (!hasText(asset.effectiveName())) throw conflict("资产名称不能为空");
        if (!hasText(asset.effectiveSummary())) throw conflict("请填写资产简介");
        if (!hasText(asset.getOwnerName())) throw conflict("请填写资产负责人");
        if (!hasText(asset.getUpdateFrequency())) throw conflict("请填写更新频率");
        if (asset.getSensitivityLevel() == null) throw conflict("请选择敏感级别");
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            String normalized = tag.trim();
            if (normalized.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单个标签不能超过 30 个字符");
            values.add(normalized);
        }
        if (values.size() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签不能超过 10 个");
        return List.copyOf(values);
    }

    private AssetResponse response(Asset asset) {
        return new AssetResponse(
                asset.getId(), asset.getAssetType(), asset.getResourceId(), asset.getDirectoryId(), asset.getStatus(),
                asset.effectiveName(), asset.effectiveSummary(), asset.getPortalName(), asset.getPortalSummary(),
                readTags(asset.getTagsJson()), asset.getOwnerName(), asset.getUpdateFrequency(), asset.getSensitivityLevel(),
                asset.isFeatured(), asset.getPublishedAt(), asset.getOfflineAt(), asset.getSourceName(), asset.getSourceCode(),
                asset.getSourceDescription(), asset.getSourceStatus(), asset.getSourceUpdatedAt(), readSnapshot(asset.getSourceSnapshot()),
                asset.getLastCheckedAt(), asset.getLastSyncedAt(), asset.getSyncStatus(), asset.getSyncError(),
                asset.getCreatedAt(), asset.getUpdatedAt()
        );
    }

    private List<String> readTags(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取资产标签", exception);
        }
    }

    private Map<String, Object> readSnapshot(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取资产来源快照", exception);
        }
    }

    private String writeJson(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private Asset requireAsset(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeMessage(Throwable exception) {
        if (exception instanceof ResponseStatusException response && response.getReason() != null) return response.getReason();
        return exception.getMessage() == null || exception.getMessage().isBlank() ? "未知错误" : exception.getMessage();
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("事务未返回预期结果");
        return value;
    }

    private record AssetReference(UUID assetId, String name, AssetType assetType, UUID resourceId, String fingerprint) {
    }
}
