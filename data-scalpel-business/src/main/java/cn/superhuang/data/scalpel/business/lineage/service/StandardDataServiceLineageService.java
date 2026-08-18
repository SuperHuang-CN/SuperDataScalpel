package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.StandardDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.service.ServiceDefinitionSnapshot;
import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves standard-service lineage from current definitions and the last successful deployment snapshot. */
@Service
public class StandardDataServiceLineageService {

    private final DataServiceRepository serviceRepository;
    private final StandardDataServiceDefinitionRepository definitionRepository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final ObjectMapper objectMapper;

    public StandardDataServiceLineageService(
            DataServiceRepository serviceRepository,
            StandardDataServiceDefinitionRepository definitionRepository,
            DataServiceDeploymentRepository deploymentRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            ObjectMapper objectMapper
    ) {
        this.serviceRepository = serviceRepository;
        this.definitionRepository = definitionRepository;
        this.deploymentRepository = deploymentRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<StandardServiceLineage> findEffectiveByModelIds(Collection<UUID> modelIds) {
        Set<UUID> requested = modelIds == null
                ? Set.of()
                : modelIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) return List.of();

        List<StandardDataServiceDefinition> definitions = definitionRepository.findAllByModelIdIn(requested);
        if (definitions.isEmpty()) return List.of();
        Set<UUID> serviceIds = definitions.stream()
                .map(StandardDataServiceDefinition::getDataServiceId)
                .collect(Collectors.toSet());
        Map<UUID, DataService> services = serviceRepository.findAllByIdIn(serviceIds).stream()
                .collect(Collectors.toMap(DataService::getId, Function.identity()));
        Map<UUID, DataServiceDeployment> deployments = deploymentRepository.findAllByDataServiceIdIn(serviceIds).stream()
                .collect(Collectors.toMap(DataServiceDeployment::getDataServiceId, Function.identity()));
        Map<UUID, DataModel> models = modelRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Map<UUID, List<DataModelField>> fieldsByModel = fieldRepository
                .findAllByModelIdInOrderByModelAndSort(requested).stream()
                .collect(Collectors.groupingBy(DataModelField::getModelId));

        return definitions.stream()
                .map(definition -> lineage(
                        services.get(definition.getDataServiceId()),
                        definition,
                        deployments.get(definition.getDataServiceId()),
                        models.get(definition.getModelId()),
                        fieldsByModel.getOrDefault(definition.getModelId(), List.of())
                ))
                .filter(Objects::nonNull)
                .filter(StandardServiceLineage::effectiveForModelGraph)
                .sorted(Comparator.comparing((StandardServiceLineage item) -> item.service().getName())
                        .thenComparing(item -> item.service().getCode())
                        .thenComparing(item -> item.service().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StandardServiceLineage> findCurrent(DataService service) {
        if (service.getType() != DataServiceType.STANDARD_TABLE) return Optional.empty();
        return definitionRepository.findByDataServiceId(service.getId()).map(definition -> {
            DataModel model = modelRepository.findById(definition.getModelId()).orElse(null);
            List<DataModelField> fields = model == null
                    ? List.of()
                    : fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
            return lineage(
                    service,
                    definition,
                    deploymentRepository.findByDataServiceId(service.getId()).orElse(null),
                    model,
                    fields
            );
        });
    }

    private StandardServiceLineage lineage(
            DataService service,
            StandardDataServiceDefinition definition,
            DataServiceDeployment deployment,
            DataModel model,
            List<DataModelField> fields
    ) {
        if (service == null || service.getType() != DataServiceType.STANDARD_TABLE) return null;
        List<DataModelField> currentFields = List.copyOf(fields);
        boolean previouslyEnabled = deployment != null && deployment.getDeployedAt() != null;
        if (service.getStatus() != DataServiceStatus.ENABLED) {
            return new StandardServiceLineage(
                    service, definition, deployment, model, currentFields,
                    currentFields.stream().map(DataModelField::getId).collect(Collectors.toUnmodifiableSet()),
                    false, previouslyEnabled, List.of()
            );
        }

        List<String> warnings = new java.util.ArrayList<>();
        if (deployment == null
                || deployment.getStatus() != DataServiceDeploymentStatus.DEPLOYED
                || deployment.getRevision() != service.getRevision()) {
            warnings.add("服务当前启用状态与部署快照不一致，无法确认实际暴露字段");
            return new StandardServiceLineage(
                    service, definition, deployment, model, currentFields,
                    Set.of(), true, previouslyEnabled, warnings
            );
        }

        List<ServiceFieldDefinition> deployedFields;
        try {
            ServiceDefinitionSnapshot snapshot = objectMapper.readValue(
                    deployment.getDefinitionJson(), ServiceDefinitionSnapshot.class
            );
            if (snapshot.type() != DataServiceType.STANDARD_TABLE || snapshot.standardDefinition() == null) {
                throw new IllegalArgumentException("部署快照不是标准单表服务");
            }
            deployedFields = snapshot.standardDefinition().fields();
            if (deployedFields == null || deployedFields.stream().anyMatch(field -> field == null
                    || field.code() == null || field.physicalColumn() == null || field.type() == null)) {
                throw new IllegalArgumentException("部署字段快照不完整");
            }
        } catch (RuntimeException exception) {
            warnings.add("服务部署字段快照无法读取，无法确认实际暴露字段");
            return new StandardServiceLineage(
                    service, definition, deployment, model, currentFields,
                    Set.of(), true, previouslyEnabled, warnings
            );
        }

        Map<String, ServiceFieldDefinition> deployedByCode = new HashMap<>();
        boolean duplicate = false;
        for (ServiceFieldDefinition field : deployedFields) {
            if (deployedByCode.put(field.code(), field) != null) duplicate = true;
        }
        Set<UUID> exposedFieldIds = currentFields.stream()
                .filter(field -> matches(field, deployedByCode.get(field.getCode())))
                .map(DataModelField::getId)
                .collect(Collectors.toUnmodifiableSet());
        boolean stale = duplicate
                || deployedFields.size() != currentFields.size()
                || exposedFieldIds.size() != currentFields.size();
        if (stale) warnings.add("服务实际部署字段与当前模型结构不一致，未匹配字段不会生成暴露关系");
        return new StandardServiceLineage(
                service, definition, deployment, model, currentFields,
                exposedFieldIds, stale, previouslyEnabled, warnings
        );
    }

    private static boolean matches(DataModelField current, ServiceFieldDefinition deployed) {
        return deployed != null
                && current.getCode().equals(deployed.code())
                && current.getCode().equals(deployed.physicalColumn())
                && current.getFieldType() == deployed.type()
                && current.isNullable() == deployed.nullable()
                && current.isPrimaryKey() == deployed.primaryKey();
    }

    public record StandardServiceLineage(
            DataService service,
            StandardDataServiceDefinition definition,
            DataServiceDeployment deployment,
            DataModel model,
            List<DataModelField> fields,
            Set<UUID> exposedFieldIds,
            boolean stale,
            boolean previouslyEnabled,
            List<String> warnings
    ) {
        public StandardServiceLineage {
            fields = List.copyOf(fields);
            exposedFieldIds = Set.copyOf(exposedFieldIds);
            warnings = List.copyOf(warnings);
        }

        public boolean effectiveForModelGraph() {
            return previouslyEnabled
                    && (service.getStatus() == DataServiceStatus.ENABLED
                    || service.getStatus() == DataServiceStatus.DISABLED);
        }

        public boolean exposes(UUID fieldId) {
            return exposedFieldIds.contains(fieldId);
        }
    }
}
