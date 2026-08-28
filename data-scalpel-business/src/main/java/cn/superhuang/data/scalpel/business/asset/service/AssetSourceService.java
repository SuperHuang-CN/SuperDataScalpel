package cn.superhuang.data.scalpel.business.asset.service;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.repository.AssetRepository;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetCandidateResponse;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceDetailResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceSummaryResponse;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryService;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryDetailResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetSourceService {

    private final DataModelService modelService;
    private final FileDatasetService fileDatasetService;
    private final StandardDictionaryService dictionaryService;
    private final DataServiceManagementService dataService;
    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    public AssetSourceService(
            DataModelService modelService,
            FileDatasetService fileDatasetService,
            StandardDictionaryService dictionaryService,
            DataServiceManagementService dataService,
            AssetRepository assetRepository,
            ObjectMapper objectMapper
    ) {
        this.modelService = modelService;
        this.fileDatasetService = fileDatasetService;
        this.dictionaryService = dictionaryService;
        this.dataService = dataService;
        this.assetRepository = assetRepository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<AssetCandidateResponse> candidates(
            AssetType assetType,
            String keyword,
            Integer page,
            Integer size
    ) {
        SearchRequest request = new SearchRequest(candidateSearch(assetType, keyword), page, size, "-updatedAt");
        return switch (assetType) {
            case DATA_MODEL -> candidatePage(assetType, modelService.search(request), DataModelResponse::id, item -> candidate(item));
            case FILE_DATASET -> candidatePage(assetType, fileDatasetService.search(request), FileDatasetResponse::id, item -> candidate(item));
            case DICTIONARY -> candidatePage(assetType, dictionaryService.search(request), StandardDictionaryResponse::id, item -> candidate(item));
            case DATA_SERVICE -> candidatePage(assetType, dataService.search(request), DataServiceSummaryResponse::id, item -> candidate(item));
        };
    }

    public AssetSourceSnapshot read(AssetType assetType, UUID resourceId) {
        return switch (assetType) {
            case DATA_MODEL -> snapshot(modelService.get(resourceId));
            case FILE_DATASET -> snapshot(fileDatasetService.get(resourceId));
            case DICTIONARY -> snapshot(dictionaryService.get(resourceId));
            case DATA_SERVICE -> snapshot(dataService.get(resourceId));
        };
    }

    private AssetSourceSnapshot snapshot(DataModelDetailResponse detail) {
        DataModelResponse model = detail.model();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", model.status().name());
        metadata.put("warehouseLayerCode", model.warehouseLayer() == null ? null : model.warehouseLayer().code());
        metadata.put("warehouseLayerName", model.warehouseLayer() == null ? null : model.warehouseLayer().name());
        metadata.put("schemaVersion", model.schemaVersion());
        metadata.put("fieldCount", detail.fields().size());
        boolean available = "PUBLISHED".equals(model.status().name());
        return createSnapshot(
                AssetType.DATA_MODEL, model.id(), model.name(), model.code(), model.description(), model.status().name(),
                model.updatedAt(), available, available ? null : "只能登记已发布的数据模型", metadata
        );
    }

    private AssetSourceSnapshot snapshot(FileDatasetResponse dataset) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("datasetType", dataset.type().name());
        metadata.put("fileCount", dataset.fileCount());
        metadata.put("tableCount", dataset.tableCount());
        metadata.put("readyTableCount", dataset.readyTableCount());
        boolean available = dataset.readyTableCount() > 0;
        String status = available ? "READY" : "NOT_READY";
        return createSnapshot(
                AssetType.FILE_DATASET, dataset.id(), dataset.name(), null, dataset.description(), status,
                dataset.updatedAt(), available, available ? null : "文件数据集至少需要一张可用表", metadata
        );
    }

    private AssetSourceSnapshot snapshot(StandardDictionaryDetailResponse detail) {
        StandardDictionaryResponse dictionary = detail.dictionary();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("enabled", dictionary.enabled());
        metadata.put("valueType", dictionary.valueType().name());
        metadata.put("contentVersion", dictionary.version());
        metadata.put("itemCount", detail.itemCount());
        String status = dictionary.enabled() ? "ENABLED" : "DISABLED";
        return createSnapshot(
                AssetType.DICTIONARY, dictionary.id(), dictionary.name(), dictionary.code(), dictionary.description(), status,
                dictionary.updatedAt(), dictionary.enabled(), dictionary.enabled() ? null : "只能登记已启用的码表", metadata
        );
    }

    private AssetSourceSnapshot snapshot(DataServiceDetailResponse service) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("serviceType", service.type().name());
        service.gatewayBindings().stream()
                .filter(binding -> "PUBLISHED".equals(binding.publicationStatus().name()))
                .findFirst()
                .ifPresent(binding -> {
                    metadata.put("accessMode", binding.accessMode().name());
                    metadata.put("routePath", binding.gatewayRoutePath());
                });
        metadata.put("definitionVersion", service.definitionVersion());
        metadata.put("deploymentStatus", service.deploymentStatus() == null ? null : service.deploymentStatus().name());
        boolean available = "ENABLED".equals(service.status().name());
        return createSnapshot(
                AssetType.DATA_SERVICE, service.id(), service.name(), service.code(), service.description(), service.status().name(),
                service.updatedAt(), available, available ? null : "只能登记已启用的数据服务", metadata
        );
    }

    private AssetCandidateResponse candidate(DataModelResponse model) {
        boolean eligible = "PUBLISHED".equals(model.status().name());
        return new AssetCandidateResponse(
                AssetType.DATA_MODEL, model.id(), model.name(), model.code(), model.description(), model.status().name(),
                model.updatedAt(), eligible, eligible ? null : "只能登记已发布的数据模型", false, null
        );
    }

    private AssetCandidateResponse candidate(FileDatasetResponse dataset) {
        boolean eligible = dataset.readyTableCount() > 0;
        return new AssetCandidateResponse(
                AssetType.FILE_DATASET, dataset.id(), dataset.name(), null, dataset.description(),
                eligible ? "READY" : "NOT_READY", dataset.updatedAt(), eligible,
                eligible ? null : "文件数据集至少需要一张可用表", false, null
        );
    }

    private AssetCandidateResponse candidate(StandardDictionaryResponse dictionary) {
        String status = dictionary.enabled() ? "ENABLED" : "DISABLED";
        return new AssetCandidateResponse(
                AssetType.DICTIONARY, dictionary.id(), dictionary.name(), dictionary.code(), dictionary.description(), status,
                dictionary.updatedAt(), dictionary.enabled(), dictionary.enabled() ? null : "只能登记已启用的码表", false, null
        );
    }

    private AssetCandidateResponse candidate(DataServiceSummaryResponse service) {
        boolean eligible = "ENABLED".equals(service.status().name());
        return new AssetCandidateResponse(
                AssetType.DATA_SERVICE, service.id(), service.name(), service.code(), service.description(), service.status().name(),
                service.updatedAt(), eligible, eligible ? null : "只能登记已启用的数据服务", false, null
        );
    }

    private AssetSourceSnapshot createSnapshot(
            AssetType assetType,
            UUID resourceId,
            String name,
            String code,
            String description,
            String sourceStatus,
            java.time.Instant sourceUpdatedAt,
            boolean available,
            String unavailableReason,
            Map<String, Object> metadata
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assetType", assetType.name());
        snapshot.put("resourceId", resourceId.toString());
        snapshot.put("name", name);
        snapshot.put("code", code);
        snapshot.put("description", description);
        snapshot.put("sourceStatus", sourceStatus);
        snapshot.put("sourceUpdatedAt", sourceUpdatedAt);
        snapshot.put("metadata", metadata);
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            return new AssetSourceSnapshot(
                    assetType, resourceId, name, code, description, sourceStatus, sourceUpdatedAt,
                    available, unavailableReason, Collections.unmodifiableMap(new LinkedHashMap<>(metadata)), json, fingerprint(json)
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法生成资产来源快照", exception);
        }
    }

    private <T> PageResponse<AssetCandidateResponse> candidatePage(
            AssetType assetType,
            PageResponse<T> page,
            Function<T, UUID> id,
            Function<T, AssetCandidateResponse> mapper
    ) {
        List<UUID> resourceIds = page.content().stream().map(id).toList();
        Map<UUID, UUID> registered = assetRepository.findAllByAssetTypeAndResourceIdIn(assetType, resourceIds).stream()
                .collect(Collectors.toMap(asset -> asset.getResourceId(), asset -> asset.getId()));
        List<AssetCandidateResponse> content = page.content().stream().map(item -> {
            AssetCandidateResponse candidate = mapper.apply(item);
            UUID assetId = registered.get(candidate.resourceId());
            return new AssetCandidateResponse(
                    candidate.assetType(), candidate.resourceId(), candidate.name(), candidate.code(), candidate.description(),
                    candidate.sourceStatus(), candidate.sourceUpdatedAt(), candidate.eligible(), candidate.ineligibleReason(),
                    assetId != null, assetId
            );
        }).toList();
        return new PageResponse<>(content, page.totalElements(), page.totalPages(), page.page(), page.size());
    }

    private static String candidateSearch(AssetType type, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        String value = keyword.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        String name = "name:*\"" + value + "\"*";
        return type == AssetType.FILE_DATASET ? name : "(" + name + " OR code:*\"" + value + "\"*)";
    }

    private static String fingerprint(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
