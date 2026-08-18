package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryTreeNodeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantDirectoryQueryService {

    private final DirectoryService directoryService;

    public AssistantDirectoryQueryService(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    public List<DirectoryItem> roots(DirectoryScope scope) {
        return snapshot(scope).roots().stream().map(SnapshotNode::toItem).toList();
    }

    public LimitedDirectories children(DirectoryScope scope, UUID parentId) {
        DirectorySnapshot snapshot = snapshot(scope);
        SnapshotNode parent = requireNode(snapshot, parentId);
        List<DirectoryItem> all = parent.childIds().stream()
                .map(snapshot.nodes()::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(SnapshotNode::sortOrder).thenComparing(SnapshotNode::name))
                .map(SnapshotNode::toItem)
                .toList();
        return limited(all, 100);
    }

    public LimitedDirectories search(DirectoryScope scope, String keyword, Integer limit) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw badRequest("目录搜索关键词不能为空");
        int resolvedLimit = limit == null ? 20 : Math.min(50, Math.max(1, limit));
        List<DirectoryItem> matches = snapshot(scope).nodes().values().stream()
                .filter(node -> node.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || node.path().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(SnapshotNode::path))
                .map(SnapshotNode::toItem)
                .toList();
        return limited(matches, resolvedLimit);
    }

    public DirectoryDetail detail(UUID id) {
        DirectoryResponse detail = directoryService.get(id);
        DirectorySnapshot snapshot = snapshot(detail.scope());
        SnapshotNode node = requireNode(snapshot, id);
        return new DirectoryDetail(
                node.id(), node.scope(), node.parentId(), node.name(), node.sortOrder(), node.description(),
                node.path(), node.depth(), node.directResourceCount(), node.resourceCount(), !node.childIds().isEmpty(),
                detail.createdAt(), detail.updatedAt()
        );
    }

    public DirectorySnapshot snapshot(DirectoryScope scope) {
        if (scope == null) throw badRequest("目录作用域不能为空");
        List<DirectoryTreeNodeResponse> tree = directoryService.tree(scope);
        Map<UUID, SnapshotNode> nodes = new LinkedHashMap<>();
        List<SnapshotNode> roots = new ArrayList<>();
        for (DirectoryTreeNodeResponse root : tree) {
            SnapshotNode snapshotRoot = append(root, null, 0, nodes);
            roots.add(snapshotRoot);
        }
        return new DirectorySnapshot(scope, Map.copyOf(nodes), List.copyOf(roots));
    }

    private SnapshotNode append(
            DirectoryTreeNodeResponse node,
            String parentPath,
            int depth,
            Map<UUID, SnapshotNode> nodes
    ) {
        String path = parentPath == null ? node.name() : parentPath + " / " + node.name();
        List<UUID> children = node.children().stream().map(DirectoryTreeNodeResponse::id).toList();
        SnapshotNode snapshot = new SnapshotNode(
                node.id(), node.scope(), node.parentId(), node.name(), node.sortOrder(), node.description(), path, depth,
                node.directResourceCount(), node.resourceCount(), children
        );
        nodes.put(node.id(), snapshot);
        for (DirectoryTreeNodeResponse child : node.children()) append(child, path, depth + 1, nodes);
        return snapshot;
    }

    private static LimitedDirectories limited(List<DirectoryItem> values, int limit) {
        boolean truncated = values.size() > limit;
        return new LimitedDirectories(values.stream().limit(limit).toList(), truncated, values.size());
    }

    private static SnapshotNode requireNode(DirectorySnapshot snapshot, UUID id) {
        SnapshotNode node = snapshot.nodes().get(id);
        if (node == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目录不存在或作用域不匹配");
        return node;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record DirectorySnapshot(
            DirectoryScope scope,
            Map<UUID, SnapshotNode> nodes,
            List<SnapshotNode> roots
    ) {
    }

    public record SnapshotNode(
            UUID id,
            DirectoryScope scope,
            UUID parentId,
            String name,
            int sortOrder,
            String description,
            String path,
            int depth,
            long directResourceCount,
            long resourceCount,
            List<UUID> childIds
    ) {
        DirectoryItem toItem() {
            return new DirectoryItem(
                    id, scope, parentId, name, sortOrder, description, path, depth,
                    directResourceCount, resourceCount, !childIds.isEmpty()
            );
        }
    }

    public record DirectoryItem(
            UUID id,
            DirectoryScope scope,
            UUID parentId,
            String name,
            int sortOrder,
            String description,
            String path,
            int depth,
            long directResourceCount,
            long resourceCount,
            boolean hasChildren
    ) {
    }

    public record DirectoryDetail(
            UUID id,
            DirectoryScope scope,
            UUID parentId,
            String name,
            int sortOrder,
            String description,
            String path,
            int depth,
            long directResourceCount,
            long resourceCount,
            boolean hasChildren,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record LimitedDirectories(List<DirectoryItem> content, boolean truncated, int totalMatches) {
    }
}
