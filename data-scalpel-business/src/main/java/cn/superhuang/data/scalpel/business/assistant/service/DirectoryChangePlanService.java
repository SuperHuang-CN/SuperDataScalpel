package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetType;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantChangeSetRepository;
import cn.superhuang.data.scalpel.business.assistant.web.request.DirectoryChangeOperationType;
import cn.superhuang.data.scalpel.business.assistant.web.request.ProposeDirectoryChangesRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DirectoryChangePlanService {

    private final AssistantDirectoryQueryService queryService;
    private final AssistantChangeSetRepository repository;
    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;

    public DirectoryChangePlanService(
            AssistantDirectoryQueryService queryService,
            AssistantChangeSetRepository repository,
            AssistantProperties properties,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssistantChangeSet propose(
            UUID sessionId,
            UUID runId,
            String username,
            ProposeDirectoryChangesRequest request
    ) {
        DirectoryChangePlanPayload payload = normalize(request);
        for (AssistantChangeSet pending : repository.findAllBySessionIdAndStatus(
                sessionId, AssistantChangeSetStatus.PENDING
        )) {
            pending.supersede();
        }
        String payloadJson = write(payload);
        return repository.saveAndFlush(AssistantChangeSet.create(
                sessionId,
                runId,
                username,
                AssistantChangeSetType.DIRECTORY,
                payload.summary(),
                payloadJson
        ));
    }

    @Transactional(readOnly = true)
    public AssistantChangeSet getOwned(UUID id, String username) {
        return repository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目录变更计划不存在"));
    }

    @Transactional(readOnly = true)
    public AssistantChangeSet pending(UUID sessionId) {
        return repository.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
                sessionId, AssistantChangeSetStatus.PENDING
        ).orElse(null);
    }

    @Transactional(readOnly = true)
    public AssistantChangeSet latest(UUID sessionId) {
        return repository.findFirstBySessionIdOrderByCreatedAtDesc(sessionId).orElse(null);
    }

    @Transactional
    public AssistantChangeSet reject(UUID id, String username) {
        AssistantChangeSet changeSet = getOwned(id, username);
        if (changeSet.getStatus() == AssistantChangeSetStatus.REJECTED) return changeSet;
        if (changeSet.getStatus() != AssistantChangeSetStatus.PENDING) {
            throw conflict("只有待确认的目录计划可以拒绝");
        }
        changeSet.reject();
        return repository.saveAndFlush(changeSet);
    }

    public DirectoryChangePlanPayload readPayload(AssistantChangeSet changeSet) {
        try {
            return objectMapper.readValue(changeSet.getPayloadJson(), DirectoryChangePlanPayload.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取目录变更计划", exception);
        }
    }

    private DirectoryChangePlanPayload normalize(ProposeDirectoryChangesRequest request) {
        if (request == null || request.scope() == null) throw badRequest("目录作用域不能为空");
        List<ProposeDirectoryChangesRequest.Operation> operations = request.operations() == null
                ? List.of() : request.operations();
        if (operations.isEmpty()) throw badRequest("目录变更计划不能为空");
        if (operations.size() > properties.maxDirectoryOperations()) {
            throw badRequest("单个目录计划最多包含 " + properties.maxDirectoryOperations() + " 个操作");
        }

        AssistantDirectoryQueryService.DirectorySnapshot snapshot = queryService.snapshot(request.scope());
        Map<UUID, AssistantDirectoryQueryService.SnapshotNode> existing = snapshot.nodes();
        List<RawCreate> creates = new ArrayList<>();
        List<RawUpdate> updates = new ArrayList<>();
        List<RawDelete> deletes = new ArrayList<>();
        Set<String> refs = new HashSet<>();
        Set<UUID> touchedIds = new HashSet<>();

        for (ProposeDirectoryChangesRequest.Operation operation : operations) {
            if (operation == null || operation.type() == null) throw badRequest("目录操作类型不能为空");
            switch (operation.type()) {
                case CREATE -> {
                    String ref = required(operation.ref(), "新建目录引用不能为空", 80);
                    if (!refs.add(ref)) throw badRequest("新建目录引用重复：“" + ref + "”");
                    requireSingleParent(operation.parentId(), operation.parentRef());
                    creates.add(new RawCreate(
                            ref,
                            operation.parentId(),
                            optional(operation.parentRef(), 80),
                            required(operation.name(), "新建目录名称不能为空", 100),
                            requiredSortOrder(operation.sortOrder()),
                            optional(operation.description(), 500)
                    ));
                }
                case UPDATE -> {
                    UUID id = requireId(operation.id(), "修改目录 ID 不能为空");
                    if (!touchedIds.add(id)) throw badRequest("同一个目录不能在计划中重复操作");
                    requireExisting(existing, id);
                    requireSingleParent(operation.parentId(), operation.parentRef());
                    updates.add(new RawUpdate(
                            id,
                            operation.parentId(),
                            optional(operation.parentRef(), 80),
                            required(operation.name(), "修改后的目录名称不能为空", 100),
                            requiredSortOrder(operation.sortOrder()),
                            optional(operation.description(), 500)
                    ));
                }
                case DELETE -> {
                    UUID id = requireId(operation.id(), "删除目录 ID 不能为空");
                    if (!touchedIds.add(id)) throw badRequest("同一个目录不能在计划中重复操作");
                    AssistantDirectoryQueryService.SnapshotNode node = requireExisting(existing, id);
                    if (!node.childIds().isEmpty()) throw conflict("目录“" + node.path() + "”包含子目录，不能删除");
                    if (node.resourceCount() > 0) throw conflict("目录“" + node.path() + "”包含业务数据，不能删除");
                    deletes.add(new RawDelete(id));
                }
            }
        }

        Set<UUID> deletedIds = deletes.stream().map(RawDelete::id).collect(java.util.stream.Collectors.toSet());
        validateParents(existing, creates, updates, refs, deletedIds);

        Map<String, NodeState> finalNodes = new LinkedHashMap<>();
        for (AssistantDirectoryQueryService.SnapshotNode node : existing.values()) {
            if (!deletedIds.contains(node.id())) {
                finalNodes.put(idKey(node.id()), new NodeState(
                        idKey(node.id()), node.parentId() == null ? null : idKey(node.parentId()), node.name()
                ));
            }
        }
        for (RawUpdate update : updates) {
            finalNodes.put(idKey(update.id()), new NodeState(
                    idKey(update.id()), parentKey(update.parentId(), update.parentRef()), update.name()
            ));
        }
        for (RawCreate create : creates) {
            finalNodes.put(refKey(create.ref()), new NodeState(
                    refKey(create.ref()), parentKey(create.parentId(), create.parentRef()), create.name()
            ));
        }
        validateFinalGraph(finalNodes);
        validateCurrentMoveBoundaries(snapshot, updates);
        validateTransientNameConflicts(snapshot, creates, updates, deletedIds);

        Map<String, String> pathCache = new HashMap<>();
        List<DirectoryChangePlanPayload.CreateOperation> normalizedCreates = creates.stream().map(create ->
                new DirectoryChangePlanPayload.CreateOperation(
                        create.ref(), create.parentId(), create.parentRef(), create.name(), create.sortOrder(),
                        create.description(), finalPath(refKey(create.ref()), finalNodes, pathCache, new HashSet<>())
                )).toList();
        List<DirectoryChangePlanPayload.UpdateOperation> normalizedUpdates = updates.stream().map(update -> {
            AssistantDirectoryQueryService.SnapshotNode node = existing.get(update.id());
            AssistantDirectoryQueryService.DirectoryDetail detail = queryService.detail(update.id());
            return new DirectoryChangePlanPayload.UpdateOperation(
                    update.id(), update.parentId(), update.parentRef(), update.name(), update.sortOrder(), update.description(),
                    detail.updatedAt(),
                    new DirectoryChangePlanPayload.CurrentState(
                            node.parentId(), node.name(), node.sortOrder(), node.description(), node.path(),
                            node.directResourceCount(), node.resourceCount()
                    ),
                    finalPath(idKey(update.id()), finalNodes, pathCache, new HashSet<>())
            );
        }).toList();
        List<DirectoryChangePlanPayload.DeleteOperation> normalizedDeletes = deletes.stream().map(delete -> {
            AssistantDirectoryQueryService.SnapshotNode node = existing.get(delete.id());
            AssistantDirectoryQueryService.DirectoryDetail detail = queryService.detail(delete.id());
            return new DirectoryChangePlanPayload.DeleteOperation(
                    delete.id(), detail.updatedAt(), node.name(), node.path(), node.directResourceCount(),
                    node.resourceCount(), node.depth()
            );
        }).toList();
        String summary = optional(request.summary(), 500);
        if (summary == null) summary = "目录变更计划（" + operations.size() + " 项）";
        return new DirectoryChangePlanPayload(
                request.scope(), summary, normalizedCreates, normalizedUpdates, normalizedDeletes
        );
    }

    private static void validateParents(
            Map<UUID, AssistantDirectoryQueryService.SnapshotNode> existing,
            List<RawCreate> creates,
            List<RawUpdate> updates,
            Set<String> refs,
            Set<UUID> deletedIds
    ) {
        for (RawCreate create : creates) {
            validateParent(existing, create.parentId(), create.parentRef(), refs, deletedIds);
        }
        for (RawUpdate update : updates) {
            validateParent(existing, update.parentId(), update.parentRef(), refs, deletedIds);
            if (update.id().equals(update.parentId())) throw badRequest("目录不能移动到自身下级");
        }
    }

    private static void validateParent(
            Map<UUID, AssistantDirectoryQueryService.SnapshotNode> existing,
            UUID parentId,
            String parentRef,
            Set<String> refs,
            Set<UUID> deletedIds
    ) {
        if (parentId != null && (!existing.containsKey(parentId) || deletedIds.contains(parentId))) {
            throw badRequest("上级目录不存在、作用域不匹配或将在本计划中删除");
        }
        if (parentRef != null && !refs.contains(parentRef)) {
            throw badRequest("新建上级目录引用不存在：“" + parentRef + "”");
        }
    }

    private static void validateFinalGraph(Map<String, NodeState> nodes) {
        Set<String> siblingNames = new HashSet<>();
        for (NodeState node : nodes.values()) {
            if (node.parentKey() != null && !nodes.containsKey(node.parentKey())) {
                throw badRequest("目录计划引用了不存在的上级目录");
            }
            String sibling = String.valueOf(node.parentKey()) + "\u0000" + node.name().toLowerCase(Locale.ROOT);
            if (!siblingNames.add(sibling)) throw conflict("目录计划会产生同级重名目录：“" + node.name() + "”");
            Set<String> visited = new HashSet<>();
            String current = node.key();
            while (current != null) {
                if (!visited.add(current)) throw badRequest("目录计划会形成循环父子关系");
                NodeState state = nodes.get(current);
                current = state == null ? null : state.parentKey();
            }
        }
    }

    private static void validateCurrentMoveBoundaries(
            AssistantDirectoryQueryService.DirectorySnapshot snapshot,
            List<RawUpdate> updates
    ) {
        for (RawUpdate update : updates) {
            if (update.parentId() == null) continue;
            UUID current = update.parentId();
            while (current != null) {
                if (current.equals(update.id())) {
                    throw badRequest("不支持在一个计划中先重排子树再把目录移动到原后代节点，请拆分操作");
                }
                AssistantDirectoryQueryService.SnapshotNode node = snapshot.nodes().get(current);
                current = node == null ? null : node.parentId();
            }
        }
    }

    private static void validateTransientNameConflicts(
            AssistantDirectoryQueryService.DirectorySnapshot snapshot,
            List<RawCreate> creates,
            List<RawUpdate> updates,
            Set<UUID> deletedIds
    ) {
        Map<String, UUID> currentNames = new HashMap<>();
        for (AssistantDirectoryQueryService.SnapshotNode node : snapshot.nodes().values()) {
            currentNames.put(siblingKey(node.parentId(), node.name()), node.id());
        }
        for (RawCreate create : creates) {
            if (create.parentRef() != null) continue;
            UUID occupant = currentNames.get(siblingKey(create.parentId(), create.name()));
            if (occupant != null && !deletedIds.contains(occupant)) {
                throw conflict("目标位置当前存在同名目录，请先单独完成重命名或移动");
            }
        }
        for (RawUpdate update : updates) {
            if (update.parentRef() != null) continue;
            UUID occupant = currentNames.get(siblingKey(update.parentId(), update.name()));
            if (occupant != null && !occupant.equals(update.id()) && !deletedIds.contains(occupant)) {
                throw conflict("目标位置当前存在同名目录，不支持在单个计划中交换名称或位置");
            }
        }
    }

    private static String finalPath(
            String key,
            Map<String, NodeState> nodes,
            Map<String, String> cache,
            Set<String> visiting
    ) {
        if (cache.containsKey(key)) return cache.get(key);
        if (!visiting.add(key)) throw badRequest("目录计划会形成循环父子关系");
        NodeState node = nodes.get(key);
        if (node == null) throw badRequest("目录计划引用了不存在的节点");
        String path = node.parentKey() == null
                ? node.name()
                : finalPath(node.parentKey(), nodes, cache, visiting) + " / " + node.name();
        visiting.remove(key);
        cache.put(key, path);
        return path;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存目录变更计划", exception);
        }
    }

    private static void requireSingleParent(UUID parentId, String parentRef) {
        if (parentId != null && parentRef != null && !parentRef.isBlank()) {
            throw badRequest("上级目录 ID 与新建目录引用只能填写一个");
        }
    }

    private static AssistantDirectoryQueryService.SnapshotNode requireExisting(
            Map<UUID, AssistantDirectoryQueryService.SnapshotNode> existing,
            UUID id
    ) {
        AssistantDirectoryQueryService.SnapshotNode node = existing.get(id);
        if (node == null) throw badRequest("目录不存在或作用域不匹配：" + id);
        return node;
    }

    private static UUID requireId(UUID value, String message) {
        if (value == null) throw badRequest(message);
        return value;
    }

    private static int requiredSortOrder(Integer value) {
        if (value == null) throw badRequest("目录排序不能为空");
        return value;
    }

    private static String required(String value, String message, int maximumLength) {
        String normalized = optional(value, maximumLength);
        if (normalized == null) throw badRequest(message);
        return normalized;
    }

    private static String optional(String value, int maximumLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximumLength) throw badRequest("字段长度不能超过 " + maximumLength + " 个字符");
        return normalized;
    }

    private static String idKey(UUID id) { return "id:" + id; }
    private static String refKey(String ref) { return "ref:" + ref; }
    private static String parentKey(UUID id, String ref) { return ref == null ? id == null ? null : idKey(id) : refKey(ref); }
    private static String siblingKey(UUID parentId, String name) {
        return String.valueOf(parentId) + "\u0000" + name.toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record RawCreate(
            String ref, UUID parentId, String parentRef, String name, int sortOrder, String description
    ) {
    }

    private record RawUpdate(
            UUID id, UUID parentId, String parentRef, String name, int sortOrder, String description
    ) {
    }

    private record RawDelete(UUID id) {
    }

    private record NodeState(String key, String parentKey, String name) {
    }
}
