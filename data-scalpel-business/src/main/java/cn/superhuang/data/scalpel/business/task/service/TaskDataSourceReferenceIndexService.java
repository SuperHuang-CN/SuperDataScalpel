package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskDataSourceReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskDataSourceReferenceLocationKind;
import cn.superhuang.data.scalpel.business.task.domain.TaskDataSourceReferenceRole;
import cn.superhuang.data.scalpel.business.task.domain.TaskDataSourceResourceKind;
import cn.superhuang.data.scalpel.business.task.repository.TaskDataSourceReferenceRepository;
import cn.superhuang.data.scalpel.contract.task.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TaskDataSourceReferenceIndexService {

    private final TaskDataSourceReferenceRepository repository;

    public TaskDataSourceReferenceIndexService(TaskDataSourceReferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replaceCanvasReferences(UUID taskId, int definitionVersion, CanvasDefinition definition) {
        replaceReferences(taskId, definitionVersion, extractCanvasReferences(definition));
    }

    /** Atomically replaces a task's rebuildable direct data-source reference index. */
    @Transactional
    public void replaceReferences(UUID taskId, int definitionVersion, List<ReferenceDraft> expected) {
        List<ReferenceKey> expectedKeys = expected.stream()
                .map(draft -> ReferenceKey.from(definitionVersion, draft))
                .sorted(ReferenceKey.ORDER)
                .toList();
        List<ReferenceKey> currentKeys = repository.findAllByTaskIdOrderByLocationKeyAsc(taskId).stream()
                .map(ReferenceKey::from)
                .sorted(ReferenceKey.ORDER)
                .toList();
        if (currentKeys.equals(expectedKeys)) {
            return;
        }
        repository.deleteAllByTaskId(taskId);
        repository.flush();
        repository.saveAll(expected.stream().map(draft -> TaskDataSourceReference.create(
                taskId,
                draft.dataSourceId(),
                definitionVersion,
                draft.role(),
                draft.locationKind(),
                draft.locationKey(),
                draft.resourceKind()
        )).toList());
    }

    public List<ReferenceDraft> extractCanvasReferences(CanvasDefinition definition) {
        return definition.nodes().stream()
                .map(this::canvasReference)
                .filter(Objects::nonNull)
                .toList();
    }

    private ReferenceDraft canvasReference(CanvasNodeDefinition node) {
        String dataSourceId = null;
        TaskDataSourceReferenceRole role = null;
        TaskDataSourceResourceKind resourceKind = null;
        if (node instanceof JdbcInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.JDBC_TABLE;
        } else if (node instanceof JdbcIncrementalInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.JDBC_TABLE;
        } else if (node instanceof JdbcQueryInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.JDBC_QUERY;
        } else if (node instanceof HttpApiInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.HTTP_API_RESOURCE;
        } else if (node instanceof SpatialServiceInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.SPATIAL_RESOURCE;
        } else if (node instanceof KafkaInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.KAFKA_TOPIC;
        } else if (node instanceof TdEngineTmqInputNodeDefinition input) {
            dataSourceId = input.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.INPUT;
            resourceKind = TaskDataSourceResourceKind.TDENGINE_TMQ_TOPIC;
        } else if (node instanceof JdbcOutputNodeDefinition output) {
            dataSourceId = output.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.OUTPUT;
            resourceKind = TaskDataSourceResourceKind.JDBC_TABLE;
        } else if (node instanceof JdbcSnapshotSyncOutputNodeDefinition output) {
            dataSourceId = output.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.OUTPUT;
            resourceKind = TaskDataSourceResourceKind.JDBC_TABLE;
        } else if (node instanceof KafkaOutputNodeDefinition output) {
            dataSourceId = output.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.OUTPUT;
            resourceKind = TaskDataSourceResourceKind.KAFKA_TOPIC;
        } else if (node instanceof FileOutputNodeDefinition output) {
            dataSourceId = output.configuration().dataSourceId();
            role = TaskDataSourceReferenceRole.OUTPUT;
            resourceKind = TaskDataSourceResourceKind.FILE_PATH;
        }
        if (dataSourceId == null || dataSourceId.isBlank()) {
            return null;
        }
        return new ReferenceDraft(
                UUID.fromString(dataSourceId), role, TaskDataSourceReferenceLocationKind.CANVAS_NODE,
                node.id(), resourceKind
        );
    }

    public record ReferenceDraft(
            UUID dataSourceId,
            TaskDataSourceReferenceRole role,
            TaskDataSourceReferenceLocationKind locationKind,
            String locationKey,
            TaskDataSourceResourceKind resourceKind
    ) {
    }

    private record ReferenceKey(
            UUID dataSourceId,
            int definitionVersion,
            TaskDataSourceReferenceRole role,
            TaskDataSourceReferenceLocationKind locationKind,
            String locationKey,
            TaskDataSourceResourceKind resourceKind
    ) {
        private static final Comparator<ReferenceKey> ORDER = Comparator
                .comparing(ReferenceKey::locationKey)
                .thenComparing(item -> item.dataSourceId().toString())
                .thenComparing(ReferenceKey::role)
                .thenComparing(ReferenceKey::locationKind)
                .thenComparing(ReferenceKey::resourceKind);

        private static ReferenceKey from(TaskDataSourceReference reference) {
            return new ReferenceKey(
                    reference.getDataSourceId(), reference.getDefinitionVersion(), reference.getReferenceRole(),
                    reference.getLocationKind(), reference.getLocationKey(), reference.getResourceKind()
            );
        }

        private static ReferenceKey from(int definitionVersion, ReferenceDraft draft) {
            return new ReferenceKey(
                    draft.dataSourceId(), definitionVersion, draft.role(), draft.locationKind(),
                    draft.locationKey(), draft.resourceKind()
            );
        }
    }
}
