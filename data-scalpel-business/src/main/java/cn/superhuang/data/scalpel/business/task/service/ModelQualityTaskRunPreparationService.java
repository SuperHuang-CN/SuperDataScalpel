package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.business.quality.repository.ModelQualityRuleRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.QualityDictionarySnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.QualityFieldSnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.QualityModelSnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.QualityRuleSnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.SkippedQualityRuleSnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelQualityTaskRunPreparationService {
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ModelQualityRuleRepository ruleRepository;
    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository dictionaryItemRepository;
    private final DialectRegistry dialectRegistry;
    private final ObjectMapper objectMapper;

    public ModelQualityTaskRunPreparationService(
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            ModelQualityRuleRepository ruleRepository,
            StandardDictionaryRepository dictionaryRepository,
            StandardDictionaryItemRepository dictionaryItemRepository,
            DialectRegistry dialectRegistry,
            ObjectMapper objectMapper
    ) {
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.ruleRepository = ruleRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryItemRepository = dictionaryItemRepository;
        this.dialectRegistry = dialectRegistry;
        this.objectMapper = objectMapper;
    }

    public Preparation prepare(UUID modelId) {
        return prepare(modelId, 0);
    }

    public Preparation prepare(UUID modelId, int failureSampleLimit) {
        DataModel target = requireModel(modelId);
        List<QualityRuleSnapshot> executable = new ArrayList<>();
        List<SkippedQualityRuleSnapshot> skipped = new ArrayList<>();
        Set<UUID> referenceModelIds = new HashSet<>();
        Set<UUID> dictionaryIds = new HashSet<>();
        List<ModelQualityRule> currentRules = ruleRepository.findAllByModelIdOrderByCreatedAtAsc(modelId);
        for (RuleAssessment assessment : assessRules(currentRules)) {
            ModelQualityRule rule = assessment.rule();
            if (!assessment.executable()) {
                skipped.add(new SkippedQualityRuleSnapshot(
                        rule.getId(), rule.getName(), rule.getRuleType(),
                        assessment.skipCode(), assessment.skipReason()));
                continue;
            }
            ModelQualityRuleDefinition definition = assessment.definition();
            if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference) {
                referenceModelIds.add(reference.targetModelId());
            }
            if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition dictionary) {
                DataModelField field = fieldRepository.findById(dictionary.fieldId()).orElse(null);
                dictionaryIds.add(field.getStandardDictionaryId());
            }
            executable.add(new QualityRuleSnapshot(
                    rule.getId(), rule.getName(), rule.getRuleType(), rule.getSeverity(), definition));
        }

        referenceModelIds.remove(target.getId());
        Map<UUID, DataModel> references = modelRepository.findAllById(referenceModelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        List<DataModel> allModels = new ArrayList<>();
        allModels.add(target);
        allModels.addAll(references.values().stream().sorted(Comparator.comparing(DataModel::getId)).toList());
        Set<UUID> sourceIds = allModels.stream().map(DataModel::getStorageDataSourceId).collect(Collectors.toSet());
        Map<UUID, DataSource> sources = dataSourceRepository.findAllById(sourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, Function.identity()));
        if (sources.size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "质检模型绑定的数据源不存在");
        }
        sources.values().forEach(this::requireReadableSource);

        ModelQualityExecutionPayload payload = new ModelQualityExecutionPayload(
                modelSnapshot(target),
                executable,
                skipped,
                references.values().stream().sorted(Comparator.comparing(DataModel::getId))
                        .map(this::modelSnapshot).toList(),
                dictionaryIds.stream().sorted().map(this::dictionarySnapshot).toList(),
                failureSampleLimit
        );
        return new Preparation(
                payload,
                sources.values().stream().sorted(Comparator.comparing(DataSource::getId))
                        .map(this::runtimeDataSource).toList(),
                sources.values().stream().collect(Collectors.toMap(DataSource::getId, DataSource::getUpdatedAt)),
                allModels.stream().collect(Collectors.toMap(DataModel::getId,
                        model -> new ModelVersion(model.getUpdatedAt(), model.getSchemaVersion()))),
                currentRules.stream()
                        .collect(Collectors.toMap(ModelQualityRule::getId, ModelQualityRule::getUpdatedAt)),
                Instant.now());
    }

    /**
     * Computes the same rule-level execution eligibility used while preparing a Manifest, without
     * opening an external JDBC connection or requiring the target model itself to be readable.
     */
    public List<RuleAssessment> assessRules(UUID modelId) {
        return assessRules(ruleRepository.findAllByModelIdOrderByCreatedAtAsc(modelId));
    }

    private List<RuleAssessment> assessRules(List<ModelQualityRule> rules) {
        List<RuleAssessment> assessments = new ArrayList<>();
        for (ModelQualityRule rule : rules) {
            if (!rule.isEnabled() || rule.getInvalidCode() != null) {
                assessments.add(RuleAssessment.skipped(
                        rule,
                        rule.getInvalidCode() == null ? "RULE_DISABLED" : rule.getInvalidCode(),
                        rule.getInvalidReason() == null ? "规则已停用" : rule.getInvalidReason()));
                continue;
            }
            ModelQualityRuleDefinition definition = readDefinition(rule);
            if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference) {
                DataModel referenceModel = modelRepository.findById(reference.targetModelId()).orElse(null);
                if (referenceModel == null) {
                    assessments.add(RuleAssessment.skipped(
                            rule, "REFERENCE_MODEL_MISSING", "引用目标模型不存在"));
                    continue;
                }
                if (!referenceModel.getId().equals(rule.getModelId())) {
                    DataSource referenceSource = dataSourceRepository
                            .findById(referenceModel.getStorageDataSourceId()).orElse(null);
                    String unavailableReason = readableSourceProblem(referenceSource);
                    if (unavailableReason != null) {
                        assessments.add(RuleAssessment.skipped(
                                rule, "REFERENCE_DEPENDENCY_UNAVAILABLE", unavailableReason));
                        continue;
                    }
                }
            }
            if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition dictionary) {
                DataModelField field = fieldRepository.findById(dictionary.fieldId()).orElse(null);
                if (field == null || field.getStandardDictionaryId() == null) {
                    assessments.add(RuleAssessment.skipped(
                            rule, "DICTIONARY_MISSING", "字段未绑定码表"));
                    continue;
                }
                StandardDictionary current = dictionaryRepository.findById(field.getStandardDictionaryId()).orElse(null);
                if (current == null || !current.isEnabled()) {
                    assessments.add(RuleAssessment.skipped(
                            rule, "DICTIONARY_DISABLED", "关联码表已停用或不存在"));
                    continue;
                }
            }
            assessments.add(RuleAssessment.executable(rule, definition));
        }
        return List.copyOf(assessments);
    }

    public void assertUnchanged(Preparation preparation) {
        for (Map.Entry<UUID, Instant> entry : preparation.dataSourceVersions().entrySet()) {
            DataSource source = dataSourceRepository.findById(entry.getKey()).orElse(null);
            if (source == null || !entry.getValue().equals(source.getUpdatedAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检数据源配置已变化，请重新运行");
            }
        }
        for (Map.Entry<UUID, ModelVersion> entry : preparation.modelVersions().entrySet()) {
            DataModel model = modelRepository.findById(entry.getKey()).orElse(null);
            if (model == null || model.getSchemaVersion() != entry.getValue().schemaVersion()
                    || !entry.getValue().updatedAt().equals(model.getUpdatedAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检模型定义已变化，请重新运行");
            }
        }
        Map<UUID, Instant> currentRules = ruleRepository.findAllByModelIdOrderByCreatedAtAsc(
                        preparation.payload().targetModel().id()).stream()
                .collect(Collectors.toMap(ModelQualityRule::getId, ModelQualityRule::getUpdatedAt));
        if (!preparation.ruleVersions().equals(currentRules)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "质检规则已变化，请重新运行");
        }
    }

    private QualityModelSnapshot modelSnapshot(DataModel model) {
        List<QualityFieldSnapshot> fields = fieldRepository
                .findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId()).stream()
                .map(field -> new QualityFieldSnapshot(
                        field.getId(), field.getCode(), field.getName(), field.getSortOrder(), field.isNullable(),
                        field.isPrimaryKey(),
                        new PlatformTypeDefinition(field.getFieldType(), field.getLength(), field.getPrecision(),
                                field.getScale(), field.getGeometry()),
                        field.getStandardDictionaryId()))
                .toList();
        return new QualityModelSnapshot(
                model.getId(), model.getCode(), model.getName(), model.getSchemaVersion(),
                model.getStorageDataSourceId(), model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), fields);
    }

    private QualityDictionarySnapshot dictionarySnapshot(UUID id) {
        StandardDictionary dictionary = dictionaryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "质检码表不存在"));
        List<StandardDictionaryItem> items = dictionaryItemRepository
                .findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(id);
        Map<UUID, StandardDictionaryItem> byId = items.stream()
                .collect(Collectors.toMap(StandardDictionaryItem::getId, Function.identity()));
        List<String> effective = items.stream()
                .filter(StandardDictionaryItem::isEnabled)
                .filter(item -> ancestorsEnabled(item, byId))
                .map(StandardDictionaryItem::getCode).distinct().toList();
        return new QualityDictionarySnapshot(id, dictionary.isEnabled(), effective);
    }

    private static boolean ancestorsEnabled(
            StandardDictionaryItem item,
            Map<UUID, StandardDictionaryItem> byId
    ) {
        Set<UUID> visited = new HashSet<>();
        UUID parentId = item.getParentId();
        while (parentId != null) {
            if (!visited.add(parentId)) return false;
            StandardDictionaryItem parent = byId.get(parentId);
            if (parent == null || !parent.isEnabled()) return false;
            parentId = parent.getParentId();
        }
        return true;
    }

    private CanvasTaskRunManifest.RuntimeDataSource runtimeDataSource(DataSource source) {
        DatabaseDialect dialect = dialectRegistry.require(source.getType().name());
        JdbcConnectionConfig config = source.getConnection().toJdbcConnectionConfig();
        JdbcConnectionSpec spec = dialect.createConnectionSpec(config);
        Map<String, String> properties = new LinkedHashMap<>();
        Properties jdbcProperties = spec.properties();
        jdbcProperties.stringPropertyNames().stream().sorted().forEach(key -> {
            if (!"user".equalsIgnoreCase(key) && !"password".equalsIgnoreCase(key)) {
                properties.put(key, jdbcProperties.getProperty(key));
            }
        });
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(), ConnectionKind.JDBC,
                CanvasTaskRunManifest.RuntimeDatabaseType.valueOf(source.getType().name()),
                source.getPurposes().stream().map(purpose -> DataSourcePurpose.valueOf(purpose.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                new CanvasTaskRunManifest.RuntimeJdbcConnection(
                        spec.driverClassName(), spec.jdbcUrl(), dialect.resolveCatalog(config, null),
                        dialect.resolveSchema(config, null), config.username(), config.password(), properties),
                null, List.of(), null, null, List.of(), null);
    }

    private void requireReadableSource(DataSource source) {
        String problem = readableSourceProblem(source);
        if (problem != null) throw new ResponseStatusException(HttpStatus.CONFLICT, problem);
    }

    private String readableSourceProblem(DataSource source) {
        if (source == null) return "质检模型绑定的数据源不存在";
        if (!source.isEnabled() || !source.getType().isJdbc()
                || !source.getPurposes().contains(cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose.SOURCE)
                && !source.getPurposes().contains(cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose.STORAGE)) {
            return "质检模型绑定的数据源不可读";
        }
        try {
            // These validations do not open a JDBC connection. Every JDBC data-source type must
            // have a dialect and a matching runtime Manifest type before it can reach the Runner.
            dialectRegistry.require(source.getType().name());
            CanvasTaskRunManifest.RuntimeDatabaseType.valueOf(source.getType().name());
            return null;
        } catch (RuntimeException exception) {
            return "质检模型绑定的数据源不受当前 Spark Runner 支持";
        }
    }

    private DataModel requireModel(UUID id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "质检目标模型不存在"));
    }

    private ModelQualityRuleDefinition readDefinition(ModelQualityRule rule) {
        try {
            ModelQualityRuleDefinition definition = objectMapper.readValue(
                    rule.getDefinitionJson(), ModelQualityRuleDefinition.class);
            if (definition.ruleType() != rule.getRuleType()) throw new IllegalStateException("规则类型不一致");
            return definition;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的质量规则定义无效：" + rule.getId(), exception);
        }
    }

    public record Preparation(
            ModelQualityExecutionPayload payload,
            List<CanvasTaskRunManifest.RuntimeDataSource> runtimeDataSources,
            Map<UUID, Instant> dataSourceVersions,
            Map<UUID, ModelVersion> modelVersions,
            Map<UUID, Instant> ruleVersions,
            Instant ruleSnapshotAt
    ) {
        public Preparation {
            runtimeDataSources = List.copyOf(runtimeDataSources);
            dataSourceVersions = Map.copyOf(dataSourceVersions);
            modelVersions = Map.copyOf(modelVersions);
            ruleVersions = Map.copyOf(ruleVersions);
            if (ruleSnapshotAt == null) throw new IllegalArgumentException("质检规则快照时间不能为空");
        }
    }

    public record ModelVersion(Instant updatedAt, int schemaVersion) {
    }

    public record RuleAssessment(
            ModelQualityRule rule,
            ModelQualityRuleDefinition definition,
            String skipCode,
            String skipReason
    ) {
        public boolean executable() {
            return skipCode == null;
        }

        private static RuleAssessment executable(
                ModelQualityRule rule,
                ModelQualityRuleDefinition definition
        ) {
            return new RuleAssessment(rule, definition, null, null);
        }

        private static RuleAssessment skipped(ModelQualityRule rule, String code, String reason) {
            return new RuleAssessment(rule, null, code, reason);
        }
    }
}
