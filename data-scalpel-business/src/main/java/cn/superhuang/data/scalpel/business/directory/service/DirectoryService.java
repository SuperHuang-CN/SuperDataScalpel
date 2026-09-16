package cn.superhuang.data.scalpel.business.directory.service;

import cn.superhuang.data.scalpel.business.asset.repository.AssetRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.directory.web.request.CreateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.request.UpdateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryImportResultResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryTreeNodeResponse;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerRepository;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DirectoryService {

    private final cn.superhuang.data.scalpel.business.metric.repository.DataMetricRepository metricRepository;
    private final DirectoryRepository repository;
    private final AssetRepository assetRepository;
    private final DataSourceRepository dataSourceRepository;
    private final FileDatasetRepository fileDatasetRepository;
    private final DataModelRepository dataModelRepository;
    private final DataTaskRepository dataTaskRepository;
    private final DataServiceRepository dataServiceRepository;
    private final McpServerRepository mcpServerRepository;
    private final cn.superhuang.data.scalpel.business.panorama.repository.PanoramaRepository panoramaRepository;
    private final BusinessObjectTypeRepository businessObjectTypeRepository;

    public DirectoryService(
            cn.superhuang.data.scalpel.business.metric.repository.DataMetricRepository metricRepository,
            DirectoryRepository repository,
            AssetRepository assetRepository,
            DataSourceRepository dataSourceRepository,
            FileDatasetRepository fileDatasetRepository,
            DataModelRepository dataModelRepository,
            DataTaskRepository dataTaskRepository,
            DataServiceRepository dataServiceRepository,
            McpServerRepository mcpServerRepository,
            cn.superhuang.data.scalpel.business.panorama.repository.PanoramaRepository panoramaRepository,
            BusinessObjectTypeRepository businessObjectTypeRepository
    ) {
        this.metricRepository = metricRepository;
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.fileDatasetRepository = fileDatasetRepository;
        this.dataModelRepository = dataModelRepository;
        this.dataTaskRepository = dataTaskRepository;
        this.dataServiceRepository = dataServiceRepository;
        this.mcpServerRepository = mcpServerRepository;
        this.panoramaRepository = panoramaRepository;
        this.businessObjectTypeRepository = businessObjectTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<DirectoryTreeNodeResponse> tree(DirectoryScope scope) {
        List<Directory> directories = repository.findAllByScopeOrderBySortOrderAscNameAsc(scope);
        Map<UUID, List<Directory>> childrenByParentId = new HashMap<>();
        for (Directory directory : directories) {
            childrenByParentId.computeIfAbsent(directory.getParentId(), ignored -> new ArrayList<>()).add(directory);
        }
        Map<UUID, Long> directCounts = directResourceCounts(scope, directories);
        return childrenByParentId.getOrDefault(null, List.of()).stream()
                .map(directory -> toTreeNode(directory, childrenByParentId, directCounts))
                .toList();
    }

    @Transactional(readOnly = true)
    public DirectoryResponse get(UUID id) {
        return DirectoryResponse.from(requireDirectory(id));
    }

    @Transactional(readOnly = true)
    public DirectoryPathIndex pathIndex(DirectoryScope scope) {
        List<Directory> directories = repository.findAllByScopeOrderBySortOrderAscNameAsc(scope);
        Map<UUID, Directory> directoriesById = directories.stream()
                .collect(java.util.stream.Collectors.toMap(Directory::getId, directory -> directory));
        Map<UUID, String> pathsById = new LinkedHashMap<>();
        Set<UUID> resolved = new HashSet<>();
        List<String> issues = new ArrayList<>();
        for (Directory directory : directories) {
            resolveDirectoryPath(directory, directoriesById, pathsById, resolved, new HashSet<>(), issues);
        }
        return new DirectoryPathIndex(pathsById, issues);
    }

    @Transactional(readOnly = true)
    public List<DirectoryImportRow> exportRows(DirectoryScope scope) {
        List<Directory> directories = repository.findAllByScopeOrderBySortOrderAscNameAsc(scope);
        Map<UUID, List<Directory>> childrenByParentId = new HashMap<>();
        for (Directory directory : directories) {
            childrenByParentId.computeIfAbsent(directory.getParentId(), ignored -> new ArrayList<>()).add(directory);
        }
        AtomicInteger sequence = new AtomicInteger();
        List<DirectoryImportRow> rows = new ArrayList<>(directories.size());
        appendExportRows(childrenByParentId.getOrDefault(null, List.of()), null, childrenByParentId, sequence, rows);
        return List.copyOf(rows);
    }

    @Transactional
    public DirectoryImportResultResponse importRows(DirectoryScope scope, List<DirectoryImportRow> rows) {
        List<Directory> existing = repository.findAllByScopeOrderBySortOrderAscNameAsc(scope);
        Map<SiblingName, Directory> directoriesBySiblingName = new HashMap<>();
        for (Directory directory : existing) {
            directoriesBySiblingName.putIfAbsent(
                    new SiblingName(directory.getParentId(), directory.getName().toLowerCase(Locale.ROOT)),
                    directory
            );
        }
        Map<String, UUID> directoryIdsByRowKey = new HashMap<>();
        Set<String> processedRowKeys = new HashSet<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (DirectoryImportRow row : rows) {
            UUID parentId = row.parentRowKey() == null ? null : directoryIdsByRowKey.get(row.parentRowKey());
            if (row.parentRowKey() != null && parentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录导入顺序无效，父目录尚未解析");
            }
            if (!processedRowKeys.add(row.rowKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录行标识重复：“" + row.rowKey() + "”");
            }
            SiblingName siblingName = new SiblingName(parentId, row.name().toLowerCase(Locale.ROOT));
            Directory directory = directoriesBySiblingName.get(siblingName);
            if (directory == null) {
                directory = repository.save(Directory.create(
                        scope, parentId, row.name(), row.sortOrder(), row.description()
                ));
                directoriesBySiblingName.put(siblingName, directory);
                created++;
            } else if (directoryChanged(directory, row)) {
                directory.update(parentId, row.name(), row.sortOrder(), row.description());
                updated++;
            } else {
                unchanged++;
            }
            directoryIdsByRowKey.put(row.rowKey(), directory.getId());
        }
        repository.flush();
        return new DirectoryImportResultResponse(rows.size(), created, updated, unchanged);
    }

    @Transactional
    public DirectoryResponse create(CreateDirectoryRequest request) {
        validateParent(request.scope(), request.parentId(), null);
        validateSiblingName(request.scope(), request.parentId(), request.name(), null);
        Directory directory = Directory.create(
                request.scope(), request.parentId(), request.name(), request.sortOrder(), request.description()
        );
        return DirectoryResponse.from(repository.saveAndFlush(directory));
    }

    @Transactional
    public DirectoryResponse update(UUID id, UpdateDirectoryRequest request) {
        Directory directory = requireDirectory(id);
        validateParent(directory.getScope(), request.parentId(), id);
        validateSiblingName(directory.getScope(), request.parentId(), request.name(), id);
        directory.update(request.parentId(), request.name(), request.sortOrder(), request.description());
        return DirectoryResponse.from(repository.saveAndFlush(directory));
    }

    @Transactional
    public void delete(UUID id) {
        Directory directory = requireDirectory(id);
        if (repository.existsByParentId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录包含子目录，不能删除");
        }
        if (hasResources(directory.getScope(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录包含业务数据，不能删除");
        }
        repository.delete(directory);
    }

    /** Validates a scalar directory reference owned by another business entity. */
    @Transactional(readOnly = true)
    public void validateAssignment(DirectoryScope scope, UUID directoryId) {
        if (directoryId == null) {
            return;
        }
        Directory directory = requireDirectory(directoryId);
        if (directory.getScope() != scope) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录类型不匹配");
        }
    }

    private DirectoryTreeNodeResponse toTreeNode(
            Directory directory,
            Map<UUID, List<Directory>> childrenByParentId,
            Map<UUID, Long> directCounts
    ) {
        List<DirectoryTreeNodeResponse> children = childrenByParentId.getOrDefault(directory.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParentId, directCounts))
                .toList();
        long directCount = directCounts.getOrDefault(directory.getId(), 0L);
        long resourceCount = directCount + children.stream().mapToLong(DirectoryTreeNodeResponse::resourceCount).sum();
        return new DirectoryTreeNodeResponse(
                directory.getId(), directory.getScope(), directory.getParentId(), directory.getName(), directory.getSortOrder(),
                directory.getDescription(), directCount, resourceCount, children
        );
    }

    private static void appendExportRows(
            List<Directory> directories,
            String parentRowKey,
            Map<UUID, List<Directory>> childrenByParentId,
            AtomicInteger sequence,
            List<DirectoryImportRow> rows
    ) {
        for (Directory directory : directories) {
            String rowKey = "D" + String.format(Locale.ROOT, "%06d", sequence.incrementAndGet());
            rows.add(new DirectoryImportRow(
                    rowKey,
                    parentRowKey,
                    directory.getName(),
                    directory.getSortOrder(),
                    directory.getDescription()
            ));
            appendExportRows(
                    childrenByParentId.getOrDefault(directory.getId(), List.of()),
                    rowKey,
                    childrenByParentId,
                    sequence,
                    rows
            );
        }
    }

    private static boolean directoryChanged(Directory directory, DirectoryImportRow row) {
        String description = row.description() == null || row.description().trim().isEmpty()
                ? null : row.description().trim();
        return !directory.getName().equals(row.name().trim())
                || directory.getSortOrder() != row.sortOrder()
                || !Objects.equals(directory.getDescription(), description);
    }

    private static String resolveDirectoryPath(
            Directory directory,
            Map<UUID, Directory> directoriesById,
            Map<UUID, String> pathsById,
            Set<UUID> resolved,
            Set<UUID> visiting,
            List<String> issues
    ) {
        if (resolved.contains(directory.getId())) {
            return pathsById.get(directory.getId());
        }
        if (!visiting.add(directory.getId())) {
            issues.add("目录树存在循环引用，无法解析目录：\"" + directory.getName() + "\"");
            resolved.add(directory.getId());
            pathsById.put(directory.getId(), null);
            return null;
        }
        String path = null;
        if (directory.getName().contains("/")) {
            issues.add("目录名称包含路径分隔符 /，不能用于模型 Excel：\"" + directory.getName() + "\"");
        } else if (directory.getParentId() == null) {
            path = directory.getName();
        } else {
            Directory parent = directoriesById.get(directory.getParentId());
            if (parent == null) {
                issues.add("目录“" + directory.getName() + "”引用了不存在或不同作用域的上级目录");
            } else {
                String parentPath = resolveDirectoryPath(
                        parent, directoriesById, pathsById, resolved, visiting, issues
                );
                if (parentPath != null) {
                    path = parentPath + "/" + directory.getName();
                }
            }
        }
        visiting.remove(directory.getId());
        resolved.add(directory.getId());
        pathsById.put(directory.getId(), path);
        return path;
    }

    private Map<UUID, Long> directResourceCounts(DirectoryScope scope, List<Directory> directories) {
        if (directories.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> directoryIds = directories.stream().map(Directory::getId).toList();
        if (scope == DirectoryScope.METRIC) {
            for (var count : metricRepository.countByDirectoryIdIn(directoryIds)) counts.put(count.directoryId(), count.resourceCount());
        } else if (scope == DirectoryScope.BUSINESS_OBJECT) {
            for (var count : businessObjectTypeRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.DATA_SOURCE) {
            for (DataSourceRepository.DirectoryResourceCount count : dataSourceRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.PANORAMA) {
            for (var count : panoramaRepository.countByDirectoryIdIn(directoryIds)) counts.put(count.directoryId(), count.resourceCount());
        } else if (scope == DirectoryScope.FILE_DATASET) {
            for (FileDatasetRepository.DirectoryResourceCount count : fileDatasetRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.MODEL) {
            for (DataModelRepository.DirectoryResourceCount count : dataModelRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.TASK) {
            for (DataTaskRepository.DirectoryResourceCount count : dataTaskRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.DATA_SERVICE) {
            for (DataServiceRepository.DirectoryResourceCount count : dataServiceRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.MCP_SERVER) {
            for (McpServerRepository.DirectoryResourceCount count : mcpServerRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.ASSET) {
            for (AssetRepository.DirectoryResourceCount count : assetRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        }
        return counts;
    }

    private boolean hasResources(DirectoryScope scope, UUID directoryId) {
        return switch (scope) {
            case DATA_SOURCE -> dataSourceRepository.existsByDirectoryId(directoryId);
            case PANORAMA -> panoramaRepository.existsByDirectoryId(directoryId);
            case FILE_DATASET -> fileDatasetRepository.existsByDirectoryId(directoryId);
            case MODEL -> dataModelRepository.existsByDirectoryId(directoryId);
            case METRIC -> metricRepository.existsByDirectoryId(directoryId);
            case BUSINESS_OBJECT -> businessObjectTypeRepository.existsByDirectoryId(directoryId);
            case TASK -> dataTaskRepository.existsByDirectoryId(directoryId);
            case DATA_SERVICE -> dataServiceRepository.countByDirectoryIdIn(List.of(directoryId)).stream()
                    .anyMatch(count -> count.resourceCount() > 0);
            case MCP_SERVER -> mcpServerRepository.existsByDirectoryId(directoryId);
            case ASSET -> assetRepository.existsByDirectoryId(directoryId);
        };
    }

    private void validateParent(DirectoryScope scope, UUID parentId, UUID currentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录不能以自身为父目录");
        }
        Directory parent = requireDirectory(parentId);
        if (parent.getScope() != scope) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父目录类型不匹配");
        }
        UUID ancestorId = parent.getParentId();
        while (ancestorId != null) {
            if (ancestorId.equals(currentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录不能移动到自己的子目录下");
            }
            ancestorId = requireDirectory(ancestorId).getParentId();
        }
    }

    private void validateSiblingName(DirectoryScope scope, UUID parentId, String name, UUID currentId) {
        boolean duplicate = repository.findAllByScopeOrderBySortOrderAscNameAsc(scope).stream()
                .anyMatch(directory -> !directory.getId().equals(currentId)
                        && java.util.Objects.equals(directory.getParentId(), parentId)
                        && directory.getName().equalsIgnoreCase(name.trim()));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同级目录名称已存在");
        }
    }

    private Directory requireDirectory(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目录不存在"));
    }

    private record SiblingName(UUID parentId, String normalizedName) {
    }

    public record DirectoryPathResolution(UUID directoryId, String path, String issue) {

        public boolean resolved() {
            return issue == null;
        }
    }

    public static final class DirectoryPathIndex {

        private static final int MAX_PATH_LENGTH = 1000;

        private final Map<UUID, String> pathsById;
        private final Map<String, List<DirectoryPathReference>> directoriesByPath;
        private final List<String> issues;

        private DirectoryPathIndex(Map<UUID, String> pathsById, Collection<String> issues) {
            this.pathsById = Map.copyOf(pathsById.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    )));
            Map<String, List<DirectoryPathReference>> byPath = new LinkedHashMap<>();
            this.pathsById.forEach((id, path) -> byPath.computeIfAbsent(
                    normalizePath(path), ignored -> new ArrayList<>()
            ).add(new DirectoryPathReference(id, path)));
            this.directoriesByPath = Map.copyOf(byPath.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> List.copyOf(entry.getValue()),
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    )));
            this.issues = List.copyOf(issues);
        }

        public List<String> issues() {
            return issues;
        }

        public DirectoryPathResolution resolve(String rawPath) {
            if (rawPath == null || rawPath.trim().isEmpty()) {
                return new DirectoryPathResolution(null, "", null);
            }
            String trimmed = rawPath.trim();
            if (trimmed.length() > MAX_PATH_LENGTH) {
                return unresolved(trimmed, "模型目录路径不能超过 " + MAX_PATH_LENGTH + " 个字符");
            }
            String[] rawSegments = trimmed.split("/", -1);
            List<String> segments = new ArrayList<>(rawSegments.length);
            for (String rawSegment : rawSegments) {
                String segment = rawSegment.trim();
                if (segment.isEmpty()) {
                    return unresolved(trimmed, "模型目录路径包含空层级：" + trimmed);
                }
                segments.add(segment);
            }
            String requestedPath = String.join("/", segments);
            List<DirectoryPathReference> matches = directoriesByPath.getOrDefault(
                    normalizePath(requestedPath), List.of()
            );
            if (matches.isEmpty()) {
                return unresolved(requestedPath, "模型目录不存在：" + requestedPath);
            }
            if (matches.size() > 1) {
                return unresolved(requestedPath, "模型目录路径无法唯一匹配：" + requestedPath);
            }
            DirectoryPathReference match = matches.getFirst();
            return new DirectoryPathResolution(match.id(), match.path(), null);
        }

        public DirectoryPathResolution resolve(UUID directoryId) {
            if (directoryId == null) {
                return new DirectoryPathResolution(null, "", null);
            }
            String path = pathsById.get(directoryId);
            if (path == null) {
                return unresolved("", "模型引用的目录不存在或路径无效：" + directoryId);
            }
            return new DirectoryPathResolution(directoryId, path, null);
        }

        private static DirectoryPathResolution unresolved(String path, String issue) {
            return new DirectoryPathResolution(null, path, issue);
        }

        private static String normalizePath(String path) {
            return path.toLowerCase(Locale.ROOT);
        }
    }

    private record DirectoryPathReference(UUID id, String path) {
    }
}
