package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantChangeSetRepository;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.directory.web.request.CreateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.request.UpdateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DirectoryPlanExecutor {

    private final AssistantChangeSetRepository repository;
    private final DirectoryChangePlanService planService;
    private final AssistantDirectoryQueryService queryService;
    private final DirectoryService directoryService;
    private final ObjectMapper objectMapper;

    public DirectoryPlanExecutor(
            AssistantChangeSetRepository repository,
            DirectoryChangePlanService planService,
            AssistantDirectoryQueryService queryService,
            DirectoryService directoryService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.planService = planService;
        this.queryService = queryService;
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Execution execute(UUID changeSetId, String username) {
        AssistantChangeSet changeSet = repository.findLockedById(changeSetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目录变更计划不存在"));
        if (!changeSet.getOwnerUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目录变更计划不存在");
        }
        if (changeSet.getStatus() == AssistantChangeSetStatus.APPLIED) {
            return new Execution(changeSet, readResult(changeSet.getResultJson()));
        }
        if (changeSet.getStatus() != AssistantChangeSetStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录变更计划已不再等待确认");
        }

        DirectoryChangePlanPayload payload = planService.readPayload(changeSet);
        revalidate(payload);

        List<DirectoryExecutionResult.CreatedDirectory> created = new ArrayList<>();
        List<DirectoryExecutionResult.UpdatedDirectory> updated = new ArrayList<>();
        List<DirectoryExecutionResult.DeletedDirectory> deleted = new ArrayList<>();
        Map<String, UUID> createdIds = new HashMap<>();

        payload.deletes().stream()
                .sorted(Comparator.comparingInt(DirectoryChangePlanPayload.DeleteOperation::depth).reversed())
                .forEach(operation -> {
                    directoryService.delete(operation.id());
                    deleted.add(new DirectoryExecutionResult.DeletedDirectory(operation.id(), operation.name()));
                });

        payload.updates().stream().filter(operation -> operation.parentRef() == null).forEach(operation -> {
            DirectoryResponse response = directoryService.update(operation.id(), new UpdateDirectoryRequest(
                    operation.parentId(), operation.name(), operation.sortOrder(), operation.description()
            ));
            updated.add(new DirectoryExecutionResult.UpdatedDirectory(response.id(), response.name()));
        });

        List<DirectoryChangePlanPayload.CreateOperation> remaining = new ArrayList<>(payload.creates());
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                DirectoryChangePlanPayload.CreateOperation operation = iterator.next();
                if (operation.parentRef() != null && !createdIds.containsKey(operation.parentRef())) continue;
                UUID parentId = operation.parentRef() == null
                        ? operation.parentId() : createdIds.get(operation.parentRef());
                DirectoryResponse response = directoryService.create(new CreateDirectoryRequest(
                        payload.scope(), parentId, operation.name(), operation.sortOrder(), operation.description()
                ));
                createdIds.put(operation.ref(), response.id());
                created.add(new DirectoryExecutionResult.CreatedDirectory(operation.ref(), response.id(), response.name()));
                iterator.remove();
                progressed = true;
            }
            if (!progressed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建目录父子引用无法解析");
        }

        payload.updates().stream().filter(operation -> operation.parentRef() != null).forEach(operation -> {
            UUID parentId = createdIds.get(operation.parentRef());
            if (parentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建上级目录引用不存在");
            DirectoryResponse response = directoryService.update(operation.id(), new UpdateDirectoryRequest(
                    parentId, operation.name(), operation.sortOrder(), operation.description()
            ));
            updated.add(new DirectoryExecutionResult.UpdatedDirectory(response.id(), response.name()));
        });

        DirectoryExecutionResult result = new DirectoryExecutionResult(payload.scope(), created, updated, deleted);
        changeSet.apply(username, write(result), Instant.now());
        repository.saveAndFlush(changeSet);
        return new Execution(changeSet, result);
    }

    private void revalidate(DirectoryChangePlanPayload payload) {
        AssistantDirectoryQueryService.DirectorySnapshot snapshot = queryService.snapshot(payload.scope());
        for (DirectoryChangePlanPayload.UpdateOperation operation : payload.updates()) {
            AssistantDirectoryQueryService.DirectoryDetail detail;
            try {
                detail = queryService.detail(operation.id());
            } catch (ResponseStatusException exception) {
                throw new StaleDirectoryPlanException("待修改目录已不存在");
            }
            if (!Objects.equals(detail.updatedAt(), operation.expectedUpdatedAt())) {
                throw new StaleDirectoryPlanException("目录“" + operation.current().path() + "”已发生变化");
            }
            if (operation.parentId() != null && !snapshot.nodes().containsKey(operation.parentId())) {
                throw new StaleDirectoryPlanException("目标上级目录已不存在");
            }
        }
        for (DirectoryChangePlanPayload.DeleteOperation operation : payload.deletes()) {
            AssistantDirectoryQueryService.DirectoryDetail detail;
            try {
                detail = queryService.detail(operation.id());
            } catch (ResponseStatusException exception) {
                throw new StaleDirectoryPlanException("待删除目录已不存在");
            }
            if (!Objects.equals(detail.updatedAt(), operation.expectedUpdatedAt())) {
                throw new StaleDirectoryPlanException("目录“" + operation.path() + "”已发生变化");
            }
            if (detail.hasChildren() || detail.resourceCount() > 0) {
                throw new StaleDirectoryPlanException("目录“" + operation.path() + "”已包含子目录或业务数据");
            }
        }
        for (DirectoryChangePlanPayload.CreateOperation operation : payload.creates()) {
            if (operation.parentId() != null && !snapshot.nodes().containsKey(operation.parentId())) {
                throw new StaleDirectoryPlanException("新建目录的上级目录已不存在");
            }
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存目录执行结果", exception);
        }
    }

    private DirectoryExecutionResult readResult(String value) {
        try {
            return objectMapper.readValue(value, DirectoryExecutionResult.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取目录执行结果", exception);
        }
    }

    public record Execution(AssistantChangeSet changeSet, DirectoryExecutionResult result) {
    }
}
