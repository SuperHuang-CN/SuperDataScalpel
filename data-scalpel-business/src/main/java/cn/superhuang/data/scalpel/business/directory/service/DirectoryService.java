package cn.superhuang.data.scalpel.business.directory.service;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.directory.web.request.CreateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.request.UpdateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryTreeNodeResponse;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DirectoryService {

    private final DirectoryRepository repository;
    private final DataSourceRepository dataSourceRepository;
    private final DataModelRepository dataModelRepository;

    public DirectoryService(
            DirectoryRepository repository,
            DataSourceRepository dataSourceRepository,
            DataModelRepository dataModelRepository
    ) {
        this.repository = repository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataModelRepository = dataModelRepository;
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

    private Map<UUID, Long> directResourceCounts(DirectoryScope scope, List<Directory> directories) {
        if (directories.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> directoryIds = directories.stream().map(Directory::getId).toList();
        if (scope == DirectoryScope.DATA_SOURCE) {
            for (DataSourceRepository.DirectoryResourceCount count : dataSourceRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        } else if (scope == DirectoryScope.MODEL) {
            for (DataModelRepository.DirectoryResourceCount count : dataModelRepository.countByDirectoryIdIn(directoryIds)) {
                counts.put(count.directoryId(), count.resourceCount());
            }
        }
        return counts;
    }

    private boolean hasResources(DirectoryScope scope, UUID directoryId) {
        return switch (scope) {
            case DATA_SOURCE -> dataSourceRepository.existsByDirectoryId(directoryId);
            case MODEL -> dataModelRepository.existsByDirectoryId(directoryId);
            case DATA_SERVICE -> false;
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
}
