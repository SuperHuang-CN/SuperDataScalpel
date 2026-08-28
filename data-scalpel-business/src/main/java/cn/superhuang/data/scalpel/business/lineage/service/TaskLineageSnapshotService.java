package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.lineage.domain.*;
import cn.superhuang.data.scalpel.business.lineage.repository.*;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates and atomically publishes complete task lineage snapshots. */
@Service
public class TaskLineageSnapshotService {

    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository localDefinitionRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository modelFieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final TaskLineageSnapshotRepository snapshotRepository;
    private final TaskLineageAssetRepository assetRepository;
    private final TaskLineageAssetFieldRepository assetFieldRepository;
    private final TaskLineageFieldEdgeRepository fieldEdgeRepository;
    private final TaskLineageFieldUsageRepository fieldUsageRepository;

    public TaskLineageSnapshotService(
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository localDefinitionRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository modelFieldRepository,
            DataSourceRepository dataSourceRepository,
            TaskLineageSnapshotRepository snapshotRepository,
            TaskLineageAssetRepository assetRepository,
            TaskLineageAssetFieldRepository assetFieldRepository,
            TaskLineageFieldEdgeRepository fieldEdgeRepository,
            TaskLineageFieldUsageRepository fieldUsageRepository
    ) {
        this.taskRepository = taskRepository;
        this.localDefinitionRepository = localDefinitionRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.modelRepository = modelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.snapshotRepository = snapshotRepository;
        this.assetRepository = assetRepository;
        this.assetFieldRepository = assetFieldRepository;
        this.fieldEdgeRepository = fieldEdgeRepository;
        this.fieldUsageRepository = fieldUsageRepository;
    }

    @Transactional
    public TaskLineageSnapshotResult publish(TaskLineageSnapshotDraft draft) {
        Objects.requireNonNull(draft, "draft");
        DataTask task = taskRepository.findByIdForUpdate(Objects.requireNonNull(draft.taskId(), "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("血缘引用的任务不存在"));
        if (draft.definitionVersion() < 1 || draft.generatorVersion() < 1 || draft.coverage() == null) {
            throw new IllegalArgumentException("血缘定义版本、生成器版本和覆盖程度不能为空");
        }
        int currentDefinitionVersion = currentDefinitionVersion(task);
        if (currentDefinitionVersion != draft.definitionVersion()) {
            throw new IllegalArgumentException("血缘定义版本与任务当前定义版本不一致");
        }

        PreparedDraft prepared = prepare(draft);
        String fingerprint = fingerprint(draft, prepared);
        List<TaskLineageSnapshot> currents = snapshotRepository.findCurrentByTaskIdForUpdate(task.getId());
        if (currents.size() > 1) {
            throw new IllegalStateException("任务存在多个当前血缘快照");
        }
        TaskLineageSnapshot current = currents.isEmpty() ? null : currents.getFirst();
        if (current != null
                && current.getDefinitionVersion() == draft.definitionVersion()
                && current.getContentSha256().equals(fingerprint)) {
            return result(current, false);
        }

        Instant now = Instant.now();
        if (current != null) {
            current.retire(now);
            snapshotRepository.saveAndFlush(current);
        }
        int generation = Math.addExact(
                snapshotRepository.findMaximumGeneration(task.getId(), draft.definitionVersion()), 1
        );
        TaskLineageSnapshot snapshot = snapshotRepository.saveAndFlush(TaskLineageSnapshot.create(
                task.getId(), task.getName(), task.getType(), draft.definitionVersion(), generation,
                draft.coverage(), draft.generatorVersion(), fingerprint
        ));
        persistContent(snapshot, draft, prepared);
        return result(snapshot, true);
    }

    @Transactional
    public void retireCurrent(UUID taskId) {
        taskRepository.findByIdForUpdate(Objects.requireNonNull(taskId, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("血缘引用的任务不存在"));
        List<TaskLineageSnapshot> currents = snapshotRepository.findCurrentByTaskIdForUpdate(taskId);
        Instant now = Instant.now();
        currents.forEach(snapshot -> snapshot.retire(now));
        snapshotRepository.saveAll(currents);
    }

    @Transactional
    public void retireCurrentIfDefinitionChanged(UUID taskId, int definitionVersion) {
        taskRepository.findByIdForUpdate(Objects.requireNonNull(taskId, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("血缘引用的任务不存在"));
        List<TaskLineageSnapshot> currents = snapshotRepository.findCurrentByTaskIdForUpdate(taskId);
        Instant now = Instant.now();
        currents.stream().filter(snapshot -> snapshot.getDefinitionVersion() != definitionVersion)
                .forEach(snapshot -> snapshot.retire(now));
        snapshotRepository.saveAll(currents);
    }

    private int currentDefinitionVersion(DataTask task) {
        if (task.getType().isCanvas()) {
            return canvasDefinitionRepository.findByTaskId(task.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Canvas 任务尚未保存定义"))
                    .getVersion();
        }
        if (task.getType() == cn.superhuang.data.scalpel.business.task.domain.TaskType.SPARK_JAR) {
            return sparkJarDefinitionRepository.findByTaskId(task.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Spark JAR 任务尚未保存定义"))
                    .getVersion();
        }
        return localDefinitionRepository.findByTaskId(task.getId())
                .orElseThrow(() -> new IllegalArgumentException("本地 SQL 任务尚未保存定义"))
                .getVersion();
    }

    private PreparedDraft prepare(TaskLineageSnapshotDraft draft) {
        if (draft.assets().isEmpty()) throw new IllegalArgumentException("血缘至少需要一个资产");
        Map<String, TaskLineageSnapshotDraft.AssetDraft> assets = uniqueBy(
                draft.assets(), TaskLineageSnapshotDraft.AssetDraft::assetKey, "assetKey"
        );
        validateKeyLengths(assets.values());
        validateFlows(assets.values());
        draft.assets().forEach(TaskLineageSnapshotService::validateAssetShape);

        Set<UUID> modelIds = draft.assets().stream()
                .filter(asset -> asset.kind() == LineageAssetKind.MODEL)
                .map(TaskLineageSnapshotDraft.AssetDraft::modelId)
                .collect(Collectors.toSet());
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        if (models.size() != modelIds.size()) throw new IllegalArgumentException("血缘引用的模型不存在");

        Set<UUID> dataSourceIds = draft.assets().stream()
                .map(asset -> asset.kind() == LineageAssetKind.MODEL
                        ? models.get(asset.modelId()).getStorageDataSourceId()
                        : asset.dataSourceId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, DataSource> dataSources = dataSourceRepository.findAllById(dataSourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, Function.identity()));
        if (dataSources.size() != dataSourceIds.size()) throw new IllegalArgumentException("血缘引用的数据源不存在");
        for (TaskLineageSnapshotDraft.AssetDraft asset : draft.assets()) {
            validateAsset(asset, models, dataSources);
        }

        Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields = new LinkedHashMap<>();
        for (TaskLineageSnapshotDraft.FieldDraft field : draft.fields()) {
            FieldKey key = new FieldKey(
                    limited(field.assetKey(), 128, "field.assetKey"),
                    limited(field.fieldKey(), 128, "field.fieldKey")
            );
            if (fields.putIfAbsent(key, field) != null) throw new IllegalArgumentException("血缘字段键重复：" + key);
            if (field.sortOrder() < 0) throw new IllegalArgumentException("血缘字段顺序不能小于 0");
            TaskLineageSnapshotDraft.AssetDraft asset = requireAsset(assets, key.assetKey());
            if (asset.role() == LineageAssetRole.INPUT && field.outputEffect() != null) {
                throw new IllegalArgumentException("输入字段不能声明输出行为");
            }
            if (asset.role() == LineageAssetRole.OUTPUT && field.outputEffect() == null) {
                throw new IllegalArgumentException("输出字段必须声明输出行为");
            }
        }

        Map<UUID, DataModelField> modelFields = modelFieldRepository.findAllById(
                draft.fields().stream().map(TaskLineageSnapshotDraft.FieldDraft::modelFieldId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        for (Map.Entry<FieldKey, TaskLineageSnapshotDraft.FieldDraft> entry : fields.entrySet()) {
            validateField(entry.getKey(), entry.getValue(), assets, modelFields);
        }

        validateEdgesAndUsages(draft, assets, fields);
        validateFlowCoverage(draft, assets, fields);
        return new PreparedDraft(assets, fields, models, modelFields, dataSources);
    }

    private static void validateKeyLengths(Collection<TaskLineageSnapshotDraft.AssetDraft> assets) {
        assets.forEach(asset -> {
            limited(asset.assetKey(), 128, "assetKey");
            limited(asset.flowKey(), 128, "flowKey");
            limited(asset.originKey(), 128, "originKey");
        });
    }

    private static void validateFlows(Collection<TaskLineageSnapshotDraft.AssetDraft> assets) {
        Map<String, List<TaskLineageSnapshotDraft.AssetDraft>> flows = assets.stream()
                .collect(Collectors.groupingBy(TaskLineageSnapshotDraft.AssetDraft::flowKey));
        for (Map.Entry<String, List<TaskLineageSnapshotDraft.AssetDraft>> entry : flows.entrySet()) {
            long outputs = entry.getValue().stream().filter(asset -> asset.role() == LineageAssetRole.OUTPUT).count();
            if (outputs != 1) {
                throw new IllegalArgumentException("每条血缘输出链路必须且只能包含一个输出：" + entry.getKey());
            }
            TaskLineageSnapshotDraft.AssetDraft output = entry.getValue().stream()
                    .filter(asset -> asset.role() == LineageAssetRole.OUTPUT).findFirst().orElseThrow();
            if (output.flowCoverage() == null) {
                // V1 generators use the snapshot-wide coverage; resolved later in validateFlowCoverage.
            }
        }
    }

    private static void validateAsset(
            TaskLineageSnapshotDraft.AssetDraft asset,
            Map<UUID, DataModel> models,
            Map<UUID, DataSource> dataSources
    ) {
        Objects.requireNonNull(asset.role(), "asset.role");
        Objects.requireNonNull(asset.kind(), "asset.kind");
        if ((asset.role() == LineageAssetRole.OUTPUT) != (asset.writeMode() != null)) {
            throw new IllegalArgumentException("只有输出资产必须声明写入模式");
        }
        if (asset.kind() == LineageAssetKind.MODEL) {
            if (asset.modelId() == null || asset.modelSchemaVersion() == null || asset.modelSchemaVersion() < 1) {
                throw new IllegalArgumentException("模型资产必须声明模型 ID 和 Schema 版本");
            }
            DataModel model = models.get(asset.modelId());
            if (model == null || model.getSchemaVersion() != asset.modelSchemaVersion()) {
                throw new IllegalArgumentException("模型资产 Schema 版本已变化");
            }
            if (asset.dataSourceId() != null || asset.physicalTableName() != null) {
                throw new IllegalArgumentException("模型资产不能同时声明物理表资产字段");
            }
        } else if (asset.kind() == LineageAssetKind.JDBC_TABLE) {
            if (asset.dataSourceId() == null || !dataSources.containsKey(asset.dataSourceId())
                    || asset.physicalTableName() == null || asset.physicalTableName().isBlank()) {
                throw new IllegalArgumentException("JDBC_TABLE 资产必须声明数据源和物理表名");
            }
            if (asset.modelId() != null || asset.modelSchemaVersion() != null) {
                throw new IllegalArgumentException("物理表资产不能同时声明模型字段");
            }
            if (!dataSources.get(asset.dataSourceId()).getType().isJdbc()) {
                throw new IllegalArgumentException("JDBC_TABLE 必须引用 JDBC 数据源");
            }
        } else {
            if (asset.externalResourceType() == null || asset.resourceKey() == null
                    || asset.resourceKey().isBlank() || asset.resourceName() == null
                    || asset.resourceName().isBlank()) {
                throw new IllegalArgumentException("外部资源必须声明类型、稳定键和名称快照");
            }
            if (asset.dataSourceId() != null && !dataSources.containsKey(asset.dataSourceId())) {
                throw new IllegalArgumentException("外部资源引用的数据源不存在");
            }
        }
    }

    private static void validateAssetShape(TaskLineageSnapshotDraft.AssetDraft asset) {
        Objects.requireNonNull(asset.role(), "asset.role");
        Objects.requireNonNull(asset.kind(), "asset.kind");
        if ((asset.role() == LineageAssetRole.OUTPUT) != (asset.writeMode() != null)) {
            throw new IllegalArgumentException("只有输出资产必须声明写入模式");
        }
        if (asset.kind() == LineageAssetKind.MODEL) {
            if (asset.modelId() == null || asset.modelSchemaVersion() == null || asset.modelSchemaVersion() < 1) {
                throw new IllegalArgumentException("模型资产必须声明模型 ID 和 Schema 版本");
            }
            if (asset.dataSourceId() != null || asset.physicalTableName() != null) {
                throw new IllegalArgumentException("模型资产不能同时声明物理表资产字段");
            }
            return;
        }
        if (asset.kind() == LineageAssetKind.EXTERNAL_RESOURCE) {
            Objects.requireNonNull(asset.externalResourceType(), "asset.externalResourceType");
            limited(asset.resourceKey(), 128, "asset.resourceKey");
            limited(asset.resourceName(), 255, "asset.resourceName");
            if (asset.modelId() != null || asset.modelSchemaVersion() != null
                    || asset.physicalTableName() != null) {
                throw new IllegalArgumentException("外部资源不能同时声明模型或物理表字段");
            }
            return;
        }
        if (asset.dataSourceId() == null) {
            throw new IllegalArgumentException("JDBC_TABLE 资产必须声明数据源");
        }
        limited(asset.physicalTableName(), 128, "asset.physicalTableName");
        optionalLimited(asset.catalogName(), 128, "asset.catalogName");
        optionalLimited(asset.schemaName(), 128, "asset.schemaName");
        if (asset.modelId() != null || asset.modelSchemaVersion() != null) {
            throw new IllegalArgumentException("物理表资产不能同时声明模型字段");
        }
    }

    private static void validateField(
            FieldKey key,
            TaskLineageSnapshotDraft.FieldDraft field,
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            Map<UUID, DataModelField> modelFields
    ) {
        TaskLineageSnapshotDraft.AssetDraft asset = requireAsset(assets, key.assetKey());
        if (asset.kind() == LineageAssetKind.MODEL) {
            DataModelField modelField = modelFields.get(field.modelFieldId());
            if (modelField == null || !modelField.getModelId().equals(asset.modelId())) {
                throw new IllegalArgumentException("模型血缘字段不存在或不属于对应模型");
            }
        } else {
            if (field.modelFieldId() != null || field.columnCode() == null || field.columnCode().isBlank()) {
                throw new IllegalArgumentException("非模型字段必须使用列名且不能声明模型字段 ID");
            }
            limited(field.columnCode(), 128, "field.columnCode");
            optionalLimited(field.columnName(), 100, "field.columnName");
        }
    }

    private static void validateEdgesAndUsages(
            TaskLineageSnapshotDraft draft,
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields
    ) {
        Set<String> edgeKeys = new HashSet<>();
        Set<FieldKey> derivedTargets = new HashSet<>();
        Map<String, DerivationDefinition> derivations = new HashMap<>();
        for (TaskLineageSnapshotDraft.FieldEdgeDraft edge : draft.fieldEdges()) {
            FieldKey sourceKey = fieldKey(edge.source());
            FieldKey targetKey = fieldKey(edge.target());
            TaskLineageSnapshotDraft.FieldDraft source = requireField(fields, sourceKey);
            TaskLineageSnapshotDraft.FieldDraft target = requireField(fields, targetKey);
            TaskLineageSnapshotDraft.AssetDraft sourceAsset = requireAsset(assets, sourceKey.assetKey());
            TaskLineageSnapshotDraft.AssetDraft targetAsset = requireAsset(assets, targetKey.assetKey());
            String flowKey = required(edge.flowKey(), "edge.flowKey");
            if (!sourceAsset.flowKey().equals(flowKey) || !targetAsset.flowKey().equals(flowKey)
                    || sourceAsset.role() != LineageAssetRole.INPUT || targetAsset.role() != LineageAssetRole.OUTPUT) {
                throw new IllegalArgumentException("字段来源边必须在同一链路内从输入指向输出");
            }
            if (target.outputEffect() != LineageOutputFieldEffect.DERIVED) {
                throw new IllegalArgumentException("字段来源边的目标字段必须标记为 DERIVED");
            }
            Objects.requireNonNull(edge.derivationType(), "edge.derivationType");
            String derivationKey = limited(edge.derivationKey(), 160, "edge.derivationKey");
            optionalLimited(edge.transformNodeKey(), 128, "edge.transformNodeKey");
            DerivationDefinition definition = new DerivationDefinition(
                    targetKey, edge.derivationType(), optionalOr(edge.transformNodeKey(), "")
            );
            DerivationDefinition previous = derivations.putIfAbsent(flowKey + '\0' + derivationKey, definition);
            if (previous != null && !previous.equals(definition)) {
                throw new IllegalArgumentException("同一 derivationKey 必须表示同一目标字段和派生方式");
            }
            String uniqueness = flowKey + '\0' + derivationKey + '\0' + sourceKey + '\0' + targetKey;
            if (!edgeKeys.add(uniqueness)) throw new IllegalArgumentException("字段来源边重复");
            derivedTargets.add(targetKey);
        }
        fields.forEach((key, field) -> {
            if (field.outputEffect() == LineageOutputFieldEffect.DERIVED && !derivedTargets.contains(key)) {
                throw new IllegalArgumentException("DERIVED 输出字段必须至少有一条来源边");
            }
        });
        Set<String> usageKeys = new HashSet<>();
        for (TaskLineageSnapshotDraft.FieldUsageDraft usage : draft.fieldUsages()) {
            FieldKey fieldKey = fieldKey(usage.field());
            requireField(fields, fieldKey);
            TaskLineageSnapshotDraft.AssetDraft asset = requireAsset(assets, fieldKey.assetKey());
            String flowKey = required(usage.flowKey(), "usage.flowKey");
            if (!asset.flowKey().equals(flowKey)) throw new IllegalArgumentException("字段用途必须属于字段所在输出链路");
            String nodeKey = limited(usage.nodeKey(), 128, "usage.nodeKey");
            Objects.requireNonNull(usage.usageType(), "usage.usageType");
            if (!usageKeys.add(flowKey + '\0' + fieldKey + '\0' + nodeKey + '\0' + usage.usageType())) {
                throw new IllegalArgumentException("字段用途重复");
            }
        }
    }

    private void validateCompleteCoverage(
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields
    ) {
        if (hasUnknownOutputSource(assets.keySet(), fields.values())) {
            throw new IllegalArgumentException("FIELD_COMPLETE 血缘不能包含未知来源字段");
        }
        for (TaskLineageSnapshotDraft.AssetDraft asset : assets.values()) {
            if (asset.role() != LineageAssetRole.OUTPUT || asset.kind() != LineageAssetKind.MODEL) continue;
            Set<UUID> expected = modelFieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(asset.modelId()).stream()
                    .map(DataModelField::getId).collect(Collectors.toSet());
            Set<UUID> actual = fields.entrySet().stream()
                    .filter(entry -> entry.getKey().assetKey().equals(asset.assetKey()))
                    .map(entry -> entry.getValue().modelFieldId())
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException("FIELD_COMPLETE 必须覆盖输出模型的全部字段");
            }
        }
    }

    static boolean hasUnknownOutputSource(
            Set<String> assetKeys,
            Collection<TaskLineageSnapshotDraft.FieldDraft> fields
    ) {
        return fields.stream()
                .filter(field -> assetKeys.contains(field.assetKey()))
                .anyMatch(field -> field.outputEffect()
                        == LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE);
    }

    private void validateFlowCoverage(
            TaskLineageSnapshotDraft draft,
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields
    ) {
        Map<String, List<TaskLineageSnapshotDraft.AssetDraft>> flows = assets.values().stream()
                .collect(Collectors.groupingBy(TaskLineageSnapshotDraft.AssetDraft::flowKey));
        LineageCoverage worst = LineageCoverage.FIELD_COMPLETE;
        for (Map.Entry<String, List<TaskLineageSnapshotDraft.AssetDraft>> entry : flows.entrySet()) {
            TaskLineageSnapshotDraft.AssetDraft output = entry.getValue().stream()
                    .filter(asset -> asset.role() == LineageAssetRole.OUTPUT).findFirst().orElseThrow();
            LineageCoverage coverage = output.flowCoverage() == null ? draft.coverage() : output.flowCoverage();
            worst = worse(worst, coverage);
            boolean hasFields = fields.keySet().stream()
                    .anyMatch(field -> assets.get(field.assetKey()).flowKey().equals(entry.getKey()));
            boolean hasEdges = draft.fieldEdges().stream().anyMatch(edge -> edge.flowKey().equals(entry.getKey()));
            boolean hasUsages = draft.fieldUsages().stream().anyMatch(usage -> usage.flowKey().equals(entry.getKey()));
            if (coverage == LineageCoverage.MODEL_ONLY && (hasFields || hasEdges || hasUsages)) {
                throw new IllegalArgumentException("MODEL_ONLY 链路不能包含字段信息：" + entry.getKey());
            }
            if (coverage == LineageCoverage.FIELD_COMPLETE) {
                boolean unknown = fields.entrySet().stream()
                        .filter(field -> field.getKey().assetKey().equals(output.assetKey()))
                        .anyMatch(field -> field.getValue().outputEffect()
                                == LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE);
                if (unknown) throw new IllegalArgumentException("FIELD_COMPLETE 链路不能包含未知来源字段");
                if (output.kind() == LineageAssetKind.MODEL) {
                    validateCompleteCoverage(Map.of(output.assetKey(), output), fields);
                }
            }
        }
        if (worst != draft.coverage()) {
            throw new IllegalArgumentException("快照覆盖程度必须等于所有输出链路的最差值");
        }
    }

    private static LineageCoverage worse(LineageCoverage left, LineageCoverage right) {
        return rank(left) <= rank(right) ? left : right;
    }

    private static int rank(LineageCoverage coverage) {
        return switch (coverage) {
            case MODEL_ONLY -> 0;
            case FIELD_PARTIAL -> 1;
            case FIELD_COMPLETE -> 2;
        };
    }

    private void persistContent(
            TaskLineageSnapshot snapshot,
            TaskLineageSnapshotDraft draft,
            PreparedDraft prepared
    ) {
        Map<String, TaskLineageAsset> persistedAssets = new LinkedHashMap<>();
        for (TaskLineageSnapshotDraft.AssetDraft item : sortedAssets(draft.assets())) {
            TaskLineageAsset asset;
            if (item.kind() == LineageAssetKind.MODEL) {
                DataModel model = prepared.models().get(item.modelId());
                asset = TaskLineageAsset.model(
                        snapshot.getId(), item.assetKey(), item.flowKey(), item.originKey(), item.role(), item.writeMode(),
                        model.getId(), model.getSchemaVersion(), model.getCode(), model.getName()
                );
            } else if (item.kind() == LineageAssetKind.JDBC_TABLE) {
                DataSource source = prepared.dataSources().get(item.dataSourceId());
                asset = TaskLineageAsset.jdbcTable(
                        snapshot.getId(), item.assetKey(), item.flowKey(), item.originKey(), item.role(), item.writeMode(),
                        source.getId(), source.getName(), item.catalogName(), item.schemaName(), item.physicalTableName()
                );
                if (item.role() == LineageAssetRole.OUTPUT) {
                    asset.useFlowCoverage(item.flowCoverage() == null ? draft.coverage() : item.flowCoverage());
                }
            } else {
                DataSource source = item.dataSourceId() == null ? null : prepared.dataSources().get(item.dataSourceId());
                asset = TaskLineageAsset.externalResource(
                        snapshot.getId(), item.assetKey(), item.flowKey(), item.originKey(), item.role(), item.writeMode(),
                        item.externalResourceType(), item.dataSourceId(), source == null ? null : source.getName(),
                        item.resourceId(), item.resourceKey(), item.resourceName(),
                        item.flowCoverage() == null ? draft.coverage() : item.flowCoverage()
                );
            }
            if (item.role() == LineageAssetRole.OUTPUT && asset.getFlowCoverage() == null) {
                asset.useFlowCoverage(item.flowCoverage() == null ? draft.coverage() : item.flowCoverage());
            }
            persistedAssets.put(item.assetKey(), assetRepository.save(asset));
        }
        assetRepository.flush();

        Map<FieldKey, TaskLineageAssetField> persistedFields = new LinkedHashMap<>();
        for (Map.Entry<FieldKey, TaskLineageSnapshotDraft.FieldDraft> entry : sortedFields(prepared.fields())) {
            TaskLineageSnapshotDraft.FieldDraft item = entry.getValue();
            DataModelField modelField = item.modelFieldId() == null ? null : prepared.modelFields().get(item.modelFieldId());
            String code = modelField == null ? required(item.columnCode(), "columnCode") : modelField.getCode();
            String name = modelField == null
                    ? optionalOr(item.columnName(), code)
                    : modelField.getName();
            TaskLineageAssetField field = assetFieldRepository.save(TaskLineageAssetField.create(
                    snapshot.getId(), persistedAssets.get(item.assetKey()).getId(), item.fieldKey(), item.modelFieldId(),
                    code, name, item.sortOrder(), item.outputEffect()
            ));
            persistedFields.put(entry.getKey(), field);
        }
        assetFieldRepository.flush();

        fieldEdgeRepository.saveAll(draft.fieldEdges().stream()
                .sorted(Comparator.comparing(TaskLineageSnapshotDraft.FieldEdgeDraft::flowKey)
                        .thenComparing(TaskLineageSnapshotDraft.FieldEdgeDraft::derivationKey)
                        .thenComparing(edge -> edge.source().assetKey())
                        .thenComparing(edge -> edge.source().fieldKey())
                        .thenComparing(edge -> edge.target().assetKey())
                        .thenComparing(edge -> edge.target().fieldKey()))
                .map(item -> TaskLineageFieldEdge.create(
                        snapshot.getId(), item.flowKey(),
                        persistedFields.get(fieldKey(item.source())).getId(),
                        persistedFields.get(fieldKey(item.target())).getId(),
                        item.derivationKey(), item.derivationType(), item.transformNodeKey()
                )).toList());
        fieldUsageRepository.saveAll(draft.fieldUsages().stream()
                .sorted(Comparator.comparing(TaskLineageSnapshotDraft.FieldUsageDraft::flowKey)
                        .thenComparing(item -> item.field().assetKey())
                        .thenComparing(item -> item.field().fieldKey())
                        .thenComparing(TaskLineageSnapshotDraft.FieldUsageDraft::nodeKey)
                        .thenComparing(TaskLineageSnapshotDraft.FieldUsageDraft::usageType))
                .map(item -> TaskLineageFieldUsage.create(
                        snapshot.getId(), item.flowKey(), persistedFields.get(fieldKey(item.field())).getId(),
                        item.nodeKey(), item.usageType()
                )).toList());
    }

    private static String fingerprint(TaskLineageSnapshotDraft draft, PreparedDraft prepared) {
        StringBuilder canonical = new StringBuilder(4096);
        append(canonical, "contract", "2");
        append(canonical, "taskId", draft.taskId().toString());
        append(canonical, "definitionVersion", Integer.toString(draft.definitionVersion()));
        append(canonical, "coverage", draft.coverage().name());
        append(canonical, "generatorVersion", Integer.toString(draft.generatorVersion()));
        for (TaskLineageSnapshotDraft.AssetDraft item : sortedAssets(draft.assets())) {
            append(canonical, "asset", String.join("|",
                    item.assetKey(), item.flowKey(), item.originKey(), item.role().name(), item.kind().name(),
                    item.writeMode() == null ? "" : item.writeMode().name(),
                    Objects.toString(item.modelId(), ""), Objects.toString(item.modelSchemaVersion(), ""),
                    Objects.toString(item.dataSourceId(), ""), optionalOr(item.catalogName(), ""),
                    optionalOr(item.schemaName(), ""), optionalOr(item.physicalTableName(), ""),
                    Objects.toString(item.externalResourceType(), ""), Objects.toString(item.resourceId(), ""),
                    optionalOr(item.resourceKey(), ""), optionalOr(item.resourceName(), ""),
                    Objects.toString(item.flowCoverage(), "")
            ));
        }
        for (Map.Entry<FieldKey, TaskLineageSnapshotDraft.FieldDraft> entry : sortedFields(prepared.fields())) {
            TaskLineageSnapshotDraft.FieldDraft item = entry.getValue();
            append(canonical, "field", String.join("|",
                    entry.getKey().assetKey(), entry.getKey().fieldKey(), Objects.toString(item.modelFieldId(), ""),
                    optionalOr(item.columnCode(), ""), optionalOr(item.columnName(), ""),
                    Integer.toString(item.sortOrder()), item.outputEffect() == null ? "" : item.outputEffect().name()
            ));
        }
        draft.fieldEdges().stream()
                .sorted(Comparator.comparing(TaskLineageSnapshotDraft.FieldEdgeDraft::flowKey)
                        .thenComparing(TaskLineageSnapshotDraft.FieldEdgeDraft::derivationKey)
                        .thenComparing(edge -> edge.source().assetKey()).thenComparing(edge -> edge.source().fieldKey())
                        .thenComparing(edge -> edge.target().assetKey()).thenComparing(edge -> edge.target().fieldKey()))
                .forEach(item -> append(canonical, "edge", String.join("|",
                        item.flowKey(), item.source().assetKey(), item.source().fieldKey(),
                        item.target().assetKey(), item.target().fieldKey(), item.derivationKey(),
                        item.derivationType().name(), optionalOr(item.transformNodeKey(), "")
                )));
        draft.fieldUsages().stream()
                .sorted(Comparator.comparing(TaskLineageSnapshotDraft.FieldUsageDraft::flowKey)
                        .thenComparing(item -> item.field().assetKey()).thenComparing(item -> item.field().fieldKey())
                        .thenComparing(TaskLineageSnapshotDraft.FieldUsageDraft::nodeKey)
                        .thenComparing(TaskLineageSnapshotDraft.FieldUsageDraft::usageType))
                .forEach(item -> append(canonical, "usage", String.join("|",
                        item.flowKey(), item.field().assetKey(), item.field().fieldKey(),
                        item.nodeKey(), item.usageType().name()
                )));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static List<TaskLineageSnapshotDraft.AssetDraft> sortedAssets(
            List<TaskLineageSnapshotDraft.AssetDraft> assets
    ) {
        return assets.stream().sorted(Comparator.comparing(TaskLineageSnapshotDraft.AssetDraft::assetKey)).toList();
    }

    private static List<Map.Entry<FieldKey, TaskLineageSnapshotDraft.FieldDraft>> sortedFields(
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields
    ) {
        return fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }

    private static void append(StringBuilder target, String key, String value) {
        target.append(key.length()).append(':').append(key)
                .append(value.length()).append(':').append(value).append('\n');
    }

    private static <T> Map<String, T> uniqueBy(List<T> items, Function<T, String> keyFunction, String label) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T item : items) {
            String key = limited(keyFunction.apply(item), 128, label);
            if (result.putIfAbsent(key, item) != null) throw new IllegalArgumentException(label + " 重复：" + key);
        }
        return result;
    }

    private static TaskLineageSnapshotDraft.AssetDraft requireAsset(
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            String assetKey
    ) {
        TaskLineageSnapshotDraft.AssetDraft asset = assets.get(assetKey);
        if (asset == null) throw new IllegalArgumentException("血缘引用了不存在的资产：" + assetKey);
        return asset;
    }

    private static TaskLineageSnapshotDraft.FieldDraft requireField(
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields,
            FieldKey fieldKey
    ) {
        TaskLineageSnapshotDraft.FieldDraft field = fields.get(fieldKey);
        if (field == null) throw new IllegalArgumentException("血缘引用了不存在的字段：" + fieldKey);
        return field;
    }

    private static FieldKey fieldKey(TaskLineageSnapshotDraft.FieldReference reference) {
        if (reference == null) throw new IllegalArgumentException("字段引用不能为空");
        return new FieldKey(
                limited(reference.assetKey(), 128, "fieldReference.assetKey"),
                limited(reference.fieldKey(), 128, "fieldReference.fieldKey")
        );
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String limited(String value, int maximum, String label) {
        String result = required(value, label);
        if (!result.equals(value)) throw new IllegalArgumentException(label + "首尾不能包含空白字符");
        if (result.length() > maximum) throw new IllegalArgumentException(label + "长度不能超过 " + maximum);
        return result;
    }

    private static String optionalOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String optionalLimited(String value, int maximum, String label) {
        if (value == null || value.isBlank()) return null;
        return limited(value, maximum, label);
    }

    private static TaskLineageSnapshotResult result(TaskLineageSnapshot snapshot, boolean created) {
        return new TaskLineageSnapshotResult(
                snapshot.getId(), snapshot.getTaskId(), snapshot.getDefinitionVersion(), snapshot.getGeneration(),
                snapshot.getCoverage(), snapshot.getContentSha256(), created
        );
    }

    private record FieldKey(String assetKey, String fieldKey) implements Comparable<FieldKey> {
        @Override
        public int compareTo(FieldKey other) {
            int asset = assetKey.compareTo(other.assetKey);
            return asset != 0 ? asset : fieldKey.compareTo(other.fieldKey);
        }

        @Override
        public String toString() {
            return assetKey + "/" + fieldKey;
        }
    }

    private record DerivationDefinition(
            FieldKey target,
            LineageFieldDerivationType type,
            String transformNodeKey
    ) {
    }

    private record PreparedDraft(
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets,
            Map<FieldKey, TaskLineageSnapshotDraft.FieldDraft> fields,
            Map<UUID, DataModel> models,
            Map<UUID, DataModelField> modelFields,
            Map<UUID, DataSource> dataSources
    ) {
    }
}
