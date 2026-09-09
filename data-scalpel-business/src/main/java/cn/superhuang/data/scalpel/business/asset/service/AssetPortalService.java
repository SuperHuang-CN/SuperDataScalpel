package cn.superhuang.data.scalpel.business.asset.service;

import cn.superhuang.data.scalpel.business.asset.domain.Asset;
import cn.superhuang.data.scalpel.business.asset.domain.AssetStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.repository.AssetRepository;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetDetailResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetSummaryResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalOverviewResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalSourceDisplayStatus;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetSourceNavigationResponse;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AssetPortalService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final AssetRepository assetRepository;
    private final DirectoryRepository directoryRepository;
    private final AssetSourceService sourceService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTransaction;

    public AssetPortalService(
            AssetRepository assetRepository,
            DirectoryRepository directoryRepository,
            AssetSourceService sourceService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.assetRepository = assetRepository;
        this.directoryRepository = directoryRepository;
        this.sourceService = sourceService;
        this.objectMapper = objectMapper;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public AssetPortalOverviewResponse overview() {
        return required(readTransaction.execute(status -> {
            EnumMap<AssetType, Long> typeCounts = new EnumMap<>(AssetType.class);
            for (AssetType type : AssetType.values()) typeCounts.put(type, 0L);
            for (AssetRepository.TypeResourceCount count
                    : assetRepository.countByStatusGroupByType(AssetStatus.PUBLISHED)) {
                typeCounts.put(count.assetType(), count.resourceCount());
            }

            List<Directory> directories = directoryRepository
                    .findAllByScopeOrderBySortOrderAscNameAsc(DirectoryScope.ASSET);
            Map<UUID, List<Directory>> children = childrenByParent(directories);
            Map<UUID, Long> directCounts = publishedDirectoryCounts(directories);
            List<AssetPortalOverviewResponse.Domain> domains = children.getOrDefault(null, List.of()).stream()
                    .map(directory -> new AssetPortalOverviewResponse.Domain(
                            directory.getId(), directory.getName(), directory.getDescription(),
                            descendantCount(directory.getId(), children, directCounts)
                    ))
                    .toList();

            return new AssetPortalOverviewResponse(
                    assetRepository.countByStatus(AssetStatus.PUBLISHED),
                    Map.copyOf(typeCounts),
                    popularTags(assetRepository.findTagsJsonByStatus(AssetStatus.PUBLISHED)),
                    domains
            );
        }));
    }

    public PageResponse<AssetPortalAssetSummaryResponse> assets(
            String keyword,
            AssetType assetType,
            UUID directoryId,
            Integer page,
            Integer size
    ) {
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (effectivePage < 0) throw badRequest("page 不能小于 0");
        if (effectiveSize <= 0 || effectiveSize > MAX_PAGE_SIZE) {
            throw badRequest("size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        String normalizedKeyword = normalizeKeyword(keyword);

        return required(readTransaction.execute(status -> {
            List<Directory> directories = directoryRepository
                    .findAllByScopeOrderBySortOrderAscNameAsc(DirectoryScope.ASSET);
            Set<UUID> directoryIds = directoryId == null ? Set.of() : descendantIds(directoryId, directories);
            Specification<Asset> specification = portalSpecification(normalizedKeyword, assetType, directoryId, directoryIds);
            Page<Asset> result = assetRepository.findAll(
                    specification,
                    PageRequest.of(effectivePage, effectiveSize, Sort.by(
                            Sort.Order.desc("featured"),
                            Sort.Order.desc("publishedAt"),
                            Sort.Order.desc("id")
                    ))
            );
            Map<UUID, Directory> directoriesById = byId(directories);
            return new PageResponse<>(
                    result.getContent().stream().map(asset -> summary(asset, directoriesById)).toList(),
                    result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
            );
        }));
    }

    public AssetPortalAssetDetailResponse detail(UUID id) {
        AssetDetailSnapshot stored = required(readTransaction.execute(status -> {
            Asset asset = publishedAsset(id);
            List<Directory> directories = directoryRepository
                    .findAllByScopeOrderBySortOrderAscNameAsc(DirectoryScope.ASSET);
            return new AssetDetailSnapshot(
                    asset.getId(), asset.getAssetType(), asset.getResourceId(), asset.getPortalName(),
                    asset.getPortalSummary(), asset.getSourceName(), asset.getSourceCode(),
                    asset.getSourceDescription(), asset.getSourceStatus(), asset.getSourceUpdatedAt(),
                    readSnapshot(asset.getSourceSnapshot()), readTags(asset.getTagsJson()),
                    asset.getOwnerName(), asset.getUpdateFrequency(), asset.getSensitivityLevel(),
                    asset.getSyncStatus(), asset.isFeatured(), asset.getPublishedAt(),
                    directoryPath(asset.getDirectoryId(), byId(directories))
            );
        }));

        try {
            AssetSourceSnapshot current = sourceService.read(stored.assetType(), stored.resourceId());
            return detail(stored, current.available()
                    ? AssetPortalSourceDisplayStatus.AVAILABLE
                    : AssetPortalSourceDisplayStatus.UNAVAILABLE,
                    current.name(), current.code(), current.description(), current.sourceStatus(),
                    current.sourceUpdatedAt(), current.metadata());
        } catch (RuntimeException exception) {
            Map<String, Object> snapshot = stored.sourceSnapshot();
            return detail(
                    stored, AssetPortalSourceDisplayStatus.CACHED,
                    text(snapshot.get("name"), stored.sourceName()),
                    text(snapshot.get("code"), stored.sourceCode()),
                    text(snapshot.get("description"), stored.sourceDescription()),
                    text(snapshot.get("sourceStatus"), stored.sourceStatus()),
                    instant(snapshot.get("sourceUpdatedAt"), stored.sourceUpdatedAt()),
                    metadata(snapshot)
            );
        }
    }

    public AssetSourceNavigationResponse sourceNavigation(UUID id, Authentication authentication) {
        SourceReference source = required(readTransaction.execute(status -> {
            Asset asset = publishedAsset(id);
            return new SourceReference(asset.getAssetType(), asset.getResourceId());
        }));
        String requiredAuthority = switch (source.assetType()) {
            case DATA_MODEL -> "model.view";
            case PANORAMA -> "panorama.view";
            case FILE_DATASET -> "filedataset.view";
            case DICTIONARY -> "standard.dictionary.view";
            case DATA_SERVICE -> "service.view";
        };
        boolean permitted = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
        if (!permitted) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有查看源资源的权限");
        String path = switch (source.assetType()) {
            case DATA_MODEL -> "/model/" + source.resourceId();
            case PANORAMA -> "/panorama/" + source.resourceId();
            case FILE_DATASET -> "/file-dataset/" + source.resourceId();
            case DICTIONARY -> "/standard/dictionaries/" + source.resourceId();
            case DATA_SERVICE -> "/dataservice/" + source.resourceId();
        };
        return new AssetSourceNavigationResponse(path);
    }

    private Specification<Asset> portalSpecification(
            String keyword,
            AssetType assetType,
            UUID requestedDirectoryId,
            Set<UUID> directoryIds
    ) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), AssetStatus.PUBLISHED));
            if (assetType != null) predicates.add(builder.equal(root.get("assetType"), assetType));
            if (requestedDirectoryId != null) predicates.add(root.get("directoryId").in(directoryIds));
            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                var portalName = root.<String>get("portalName");
                var portalSummary = root.<String>get("portalSummary");
                predicates.add(builder.or(
                        builder.and(builder.isNotNull(portalName), builder.like(builder.lower(portalName), pattern, '\\')),
                        builder.and(builder.isNull(portalName), builder.like(builder.lower(root.get("sourceName")), pattern, '\\')),
                        builder.and(builder.isNotNull(portalSummary), builder.like(builder.lower(portalSummary), pattern, '\\')),
                        builder.and(builder.isNull(portalSummary), builder.like(builder.lower(root.get("sourceDescription")), pattern, '\\')),
                        builder.like(builder.lower(root.get("sourceCode")), pattern, '\\'),
                        builder.like(builder.lower(root.get("tagsJson")), pattern, '\\')
                ));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private AssetPortalAssetSummaryResponse summary(Asset asset, Map<UUID, Directory> directories) {
        return new AssetPortalAssetSummaryResponse(
                asset.getId(), asset.getAssetType(), asset.effectiveName(), asset.getSourceCode(),
                asset.effectiveSummary(), directoryPath(asset.getDirectoryId(), directories),
                readTags(asset.getTagsJson()), asset.getOwnerName(), asset.getUpdateFrequency(),
                asset.getSensitivityLevel(), asset.getSyncStatus(), asset.isFeatured(),
                asset.getPublishedAt(), asset.getSourceUpdatedAt()
        );
    }

    private AssetPortalAssetDetailResponse detail(
            AssetDetailSnapshot asset,
            AssetPortalSourceDisplayStatus displayStatus,
            String sourceName,
            String sourceCode,
            String sourceDescription,
            String sourceStatus,
            Instant sourceUpdatedAt,
            Map<String, Object> metadata
    ) {
        return new AssetPortalAssetDetailResponse(
                asset.id(), asset.assetType(), asset.portalName() == null ? sourceName : asset.portalName(),
                sourceCode, asset.portalSummary() == null ? sourceDescription : asset.portalSummary(),
                asset.directoryPath(), asset.tags(), asset.ownerName(), asset.updateFrequency(),
                asset.sensitivityLevel(), asset.syncStatus(), asset.featured(), asset.publishedAt(),
                displayStatus, sourceName, sourceCode, sourceDescription, sourceStatus,
                sourceUpdatedAt, Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
        );
    }

    private List<String> popularTags(List<String> values) {
        Map<String, Long> counts = new HashMap<>();
        for (String value : values) {
            try {
                for (String tag : readTags(value)) counts.merge(tag, 1L, Long::sum);
            } catch (RuntimeException ignored) {
                // A malformed historical tag value must not make the public overview unavailable.
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<UUID, Long> publishedDirectoryCounts(List<Directory> directories) {
        if (directories.isEmpty()) return Map.of();
        Map<UUID, Long> counts = new HashMap<>();
        for (AssetRepository.DirectoryResourceCount count : assetRepository.countByStatusAndDirectoryIdIn(
                AssetStatus.PUBLISHED, directories.stream().map(Directory::getId).toList())) {
            counts.put(count.directoryId(), count.resourceCount());
        }
        return counts;
    }

    private static long descendantCount(
            UUID id,
            Map<UUID, List<Directory>> children,
            Map<UUID, Long> directCounts
    ) {
        long count = directCounts.getOrDefault(id, 0L);
        for (Directory child : children.getOrDefault(id, List.of())) {
            count += descendantCount(child.getId(), children, directCounts);
        }
        return count;
    }

    private static Set<UUID> descendantIds(UUID directoryId, List<Directory> directories) {
        Map<UUID, Directory> byId = byId(directories);
        if (!byId.containsKey(directoryId)) throw badRequest("业务领域不存在");
        Map<UUID, List<Directory>> children = childrenByParent(directories);
        Set<UUID> result = new HashSet<>();
        collectDescendantIds(directoryId, children, result);
        return Set.copyOf(result);
    }

    private static void collectDescendantIds(
            UUID directoryId,
            Map<UUID, List<Directory>> children,
            Set<UUID> result
    ) {
        if (!result.add(directoryId)) return;
        for (Directory child : children.getOrDefault(directoryId, List.of())) {
            collectDescendantIds(child.getId(), children, result);
        }
    }

    private static Map<UUID, List<Directory>> childrenByParent(List<Directory> directories) {
        Map<UUID, List<Directory>> children = new LinkedHashMap<>();
        for (Directory directory : directories) {
            children.computeIfAbsent(directory.getParentId(), ignored -> new ArrayList<>()).add(directory);
        }
        return children;
    }

    private static Map<UUID, Directory> byId(List<Directory> directories) {
        Map<UUID, Directory> result = new HashMap<>();
        for (Directory directory : directories) result.put(directory.getId(), directory);
        return result;
    }

    private static String directoryPath(UUID directoryId, Map<UUID, Directory> directories) {
        if (directoryId == null) return null;
        List<String> names = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        UUID currentId = directoryId;
        while (currentId != null && visited.add(currentId)) {
            Directory directory = directories.get(currentId);
            if (directory == null) break;
            names.addFirst(directory.getName());
            currentId = directory.getParentId();
        }
        return names.isEmpty() ? null : String.join(" / ", names);
    }

    private List<String> readTags(String value) {
        return objectMapper.readValue(value, new TypeReference<List<String>>() { });
    }

    private Map<String, Object> readSnapshot(String value) {
        return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
    }

    private static Map<String, Object> metadata(Map<String, Object> snapshot) {
        Object value = snapshot.get("metadata");
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String text) result.put(text, item);
        });
        return result;
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static Instant instant(Object value, Instant fallback) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Asset publishedAsset(UUID id) {
        return assetRepository.findByIdAndStatus(id, AssetStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) throw badRequest("关键词不能超过 100 个字符");
        return normalized;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("事务未返回预期结果");
        return value;
    }

    private record SourceReference(AssetType assetType, UUID resourceId) {
    }

    private record AssetDetailSnapshot(
            UUID id,
            AssetType assetType,
            UUID resourceId,
            String portalName,
            String portalSummary,
            String sourceName,
            String sourceCode,
            String sourceDescription,
            String sourceStatus,
            Instant sourceUpdatedAt,
            Map<String, Object> sourceSnapshot,
            List<String> tags,
            String ownerName,
            String updateFrequency,
            cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel sensitivityLevel,
            cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus syncStatus,
            boolean featured,
            Instant publishedAt,
            String directoryPath
    ) {
    }
}
