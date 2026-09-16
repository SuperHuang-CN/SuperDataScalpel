package cn.superhuang.data.scalpel.business.ontology.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryFilterInput;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectType;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.Filter;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.MainSource;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.Property;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.Relation;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeReference;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeReferenceKind;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeReferenceRepository;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeRepository;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectPreviewCandidatesRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectPreviewRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectRelatedPreviewRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.CreateBusinessObjectTypeRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.UpdateBusinessObjectTypeDefinitionRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.UpdateBusinessObjectTypeRequest;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectIdentityResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPreviewCandidateResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPreviewCandidatesResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPreviewResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPropertyValueResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectRelatedPreviewResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectRelationResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeGraphNodeResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeGraphRelationResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeGraphResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeIssueResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeValidationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import jakarta.validation.Valid;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Current-definition business-object modelling and read-only source composition. */
@Service
@Transactional(readOnly = true)
public class BusinessObjectTypeService {

    private static final Set<String> SAVE_BLOCKING_CODES = Set.of(
            "MODEL_NOT_FOUND", "MODEL_NOT_PUBLISHED", "FIELD_NOT_FOUND", "FIELD_TYPE_UNSUPPORTED",
            "FIELD_TYPE_MISMATCH", "SOURCE_MODEL_NOT_CONFIGURED", "RELATION_TARGET_NOT_FOUND",
            "RELATION_TARGET_DISABLED", "RELATION_TARGET_INCOMPLETE", "RELATION_CARDINALITY_INVALID",
            "DUPLICATE_ID", "DUPLICATE_CODE", "DUPLICATE_SOURCE_MODEL", "DUPLICATE_ACCESS_CODE",
            "UNKNOWN_GROUP", "INVALID_MAPPING", "RELATION_PROPERTY_REQUIRED"
    );

    private final BusinessObjectTypeRepository objectTypes;
    private final BusinessObjectTypeReferenceRepository references;
    private final DataModelRepository models;
    private final DataModelFieldRepository fields;
    private final DirectoryService directories;
    private final DataModelService modelData;
    private final SearchEngine search;
    private final ObjectMapper mapper;

    public BusinessObjectTypeService(
            BusinessObjectTypeRepository objectTypes,
            BusinessObjectTypeReferenceRepository references,
            DataModelRepository models,
            DataModelFieldRepository fields,
            DirectoryService directories,
            DataModelService modelData,
            SearchEngine search,
            ObjectMapper mapper
    ) {
        this.objectTypes = objectTypes;
        this.references = references;
        this.models = models;
        this.fields = fields;
        this.directories = directories;
        this.modelData = modelData;
        this.search = search;
        this.mapper = mapper;
    }

    public PageResponse<BusinessObjectTypeResponse> search(SearchRequest request) {
        return search(request, (root, query, builder) -> builder.conjunction());
    }

    public PageResponse<BusinessObjectTypeResponse> search(
            SearchRequest request,
            Specification<BusinessObjectType> fixed
    ) {
        var page = search.search(request, BusinessObjectType.class, objectTypes, fixed);
        List<BusinessObjectType> all = objectTypes.findAll();
        ReadContext context = readContext(all, page.getContent().stream().collect(Collectors.toMap(BusinessObjectType::getId, this::definition)));
        return new PageResponse<>(
                page.getContent().stream().map(type -> response(type, context)).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    public BusinessObjectTypeResponse get(UUID id) {
        List<BusinessObjectType> all = objectTypes.findAll();
        return response(require(id), all);
    }

    public BusinessObjectTypeGraphResponse graph() {
        boolean includeModelMetadata = canViewModelMetadata();
        List<BusinessObjectType> all = objectTypes.findAll().stream()
                .sorted(Comparator.comparing(BusinessObjectType::getName)
                        .thenComparing(BusinessObjectType::getCode)
                        .thenComparing(BusinessObjectType::getId))
                .toList();
        Map<UUID, BusinessObjectType> typesById = all.stream()
                .collect(Collectors.toMap(BusinessObjectType::getId, Function.identity()));
        Map<UUID, BusinessObjectTypeDefinition> definitions = all.stream()
                .collect(Collectors.toMap(BusinessObjectType::getId, this::definition));

        Set<UUID> modelIds = definitions.values().stream()
                .map(BusinessObjectTypeDefinition::mainSource)
                .filter(Objects::nonNull)
                .map(MainSource::modelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, DataModel> modelsById = includeModelMetadata
                ? models.findAllById(modelIds).stream()
                        .collect(Collectors.toMap(DataModel::getId, Function.identity()))
                : Map.of();

        Set<UUID> fieldIds = definitions.values().stream()
                .flatMap(value -> value.relations().stream())
                .flatMap(relation -> java.util.stream.Stream.of(relation.sourceFieldId(), relation.targetFieldId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, DataModelField> fieldsById = includeModelMetadata
                ? fields.findAllById(fieldIds).stream()
                        .collect(Collectors.toMap(DataModelField::getId, Function.identity()))
                : Map.of();

        List<BusinessObjectTypeGraphNodeResponse> nodes = all.stream().map(type -> {
            BusinessObjectTypeDefinition value = definitions.get(type.getId());
            UUID mainModelId = value.mainSource() == null ? null : value.mainSource().modelId();
            DataModel mainModel = mainModelId == null ? null : modelsById.get(mainModelId);
            return new BusinessObjectTypeGraphNodeResponse(
                    type.getId(), type.getName(), type.getCode(), type.getDirectoryId(), type.isEnabled(),
                    mainModelId, mainModel == null ? null : mainModel.getName(),
                    mainModel == null ? null : mainModel.getCode(), value.properties().size(), value.groups().size(),
                    value.supplements().size(), value.capabilities().size(), type.getUpdatedAt()
            );
        }).toList();

        Set<UUID> relationIds = new HashSet<>();
        List<BusinessObjectTypeGraphRelationResponse> graphRelations = new ArrayList<>();
        for (BusinessObjectType source : all) {
            for (Relation relation : definitions.get(source.getId()).relations()) {
                if (relation.id() != null && !relationIds.add(relation.id())) continue;
                BusinessObjectType target = typesById.get(relation.targetObjectTypeId());
                DataModelField sourceField = fieldsById.get(relation.sourceFieldId());
                DataModelField targetField = fieldsById.get(relation.targetFieldId());
                graphRelations.add(new BusinessObjectTypeGraphRelationResponse(
                        relation.id(), relation.code(), source.getId(), source.getName(), relation.targetObjectTypeId(),
                        target == null ? null : target.getName(), relation.forwardName(), relation.forwardAccessCode(),
                        relation.reverseName(), relation.reverseAccessCode(), relation.description(), relation.cardinality(),
                        relation.sourceFieldId(), sourceField == null ? null : sourceField.getName(),
                        sourceField == null ? null : sourceField.getCode(), relation.targetFieldId(),
                        targetField == null ? null : targetField.getName(), targetField == null ? null : targetField.getCode()
                ));
            }
        }
        graphRelations.sort(Comparator
                .comparing(BusinessObjectTypeGraphRelationResponse::sourceObjectTypeName)
                .thenComparing(item -> Objects.toString(item.forwardName(), ""))
                .thenComparing(item -> Objects.toString(item.code(), "")));
        return new BusinessObjectTypeGraphResponse(nodes, List.copyOf(graphRelations), Instant.now());
    }

    @Transactional
    public BusinessObjectTypeResponse create(CreateBusinessObjectTypeRequest request) {
        String code = requiredCode(request.code(), "对象类型编码");
        if (objectTypes.existsByCode(code)) {
            throw error(HttpStatus.CONFLICT, "对象类型编码已存在");
        }
        directories.validateAssignment(DirectoryScope.BUSINESS_OBJECT, request.directoryId());
        BusinessObjectType type = BusinessObjectType.create(code, json(BusinessObjectTypeDefinition.empty()));
        type.update(requiredText(request.name(), "对象类型名称"), request.directoryId(),
                optionalText(request.ownerName()), optionalText(request.summary()));
        BusinessObjectType saved = objectTypes.saveAndFlush(type);
        return response(saved, List.of(saved));
    }

    @Transactional
    public BusinessObjectTypeResponse update(UUID id, UpdateBusinessObjectTypeRequest request) {
        BusinessObjectType type = locked(id);
        directories.validateAssignment(DirectoryScope.BUSINESS_OBJECT, request.directoryId());
        type.update(requiredText(request.name(), "对象类型名称"), request.directoryId(),
                optionalText(request.ownerName()), optionalText(request.summary()));
        BusinessObjectType saved = objectTypes.saveAndFlush(type);
        return response(saved, objectTypes.findAll());
    }

    @Transactional
    public BusinessObjectTypeResponse saveDefinition(UUID id, UpdateBusinessObjectTypeDefinitionRequest request) {
        BusinessObjectType type = locked(id);
        BusinessObjectTypeDefinition definition = request.definition();
        List<BusinessObjectType> all = objectTypes.findAll();
        Inspection inspection = inspect(type, definition, all);
        inspection.issues().stream()
                .filter(issue -> SAVE_BLOCKING_CODES.contains(issue.code()))
                .findFirst()
                .ifPresent(issue -> { throw error(HttpStatus.BAD_REQUEST, issue.message()); });
        assertIdentityCanChange(type, definition);
        assertReferencedPropertiesCanChange(type, definition, all);
        type.saveDefinition(json(definition));
        objectTypes.saveAndFlush(type);
        replaceReferences(type.getId(), definition);
        return response(type, objectTypes.findAll());
    }

    public BusinessObjectTypeValidationResponse validation(UUID id) {
        BusinessObjectType type = require(id);
        return inspectionResponse(inspect(type, definition(type), objectTypes.findAll()));
    }

    public BusinessObjectTypeValidationResponse health(UUID id) {
        return validation(id);
    }

    @Transactional
    public BusinessObjectTypeResponse enable(UUID id) {
        BusinessObjectType type = locked(id);
        type.enable();
        objectTypes.saveAndFlush(type);
        return response(type, objectTypes.findAll());
    }

    @Transactional
    public BusinessObjectTypeResponse disable(UUID id) {
        BusinessObjectType type = locked(id);
        assertNoInboundRelations(type.getId());
        type.disable();
        objectTypes.saveAndFlush(type);
        return response(type, objectTypes.findAll());
    }

    @Transactional
    public void delete(UUID id) {
        BusinessObjectType type = locked(id);
        assertNoInboundRelations(type.getId());
        references.deleteAllByObjectTypeId(type.getId());
        objectTypes.delete(type);
    }

    public List<BusinessObjectRelationResponse> relations(UUID id) {
        require(id);
        return availableRelations(id, objectTypes.findAll());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BusinessObjectPreviewCandidatesResponse previewCandidates(
            UUID id,
            BusinessObjectPreviewCandidatesRequest request
    ) {
        BusinessObjectType type = requireEnabled(id);
        BusinessObjectTypeDefinition definition = definition(type);
        SourceSchema main = requirePreviewMain(type, definition);
        int pageNo = request.pageNo() == null ? 1 : request.pageNo();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        DataModelDataQueryResponse data = query(
                main,
                columns(main, List.of(definition.mainSource().identityFieldId(), definition.mainSource().titleFieldId())),
                filters(definition.mainSource().fixedFilters()),
                pageNo,
                pageSize
        );
        String identityCode = main.requireField(definition.mainSource().identityFieldId()).getCode();
        String titleCode = main.requireField(definition.mainSource().titleFieldId()).getCode();
        return new BusinessObjectPreviewCandidatesResponse(
                data.rows().stream().map(row -> new BusinessObjectPreviewCandidateResponse(
                        asKey(row.get(identityCode)), asDisplay(row.get(titleCode))
                )).toList(),
                data.pageNo(), data.pageSize(), data.hasNext()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BusinessObjectPreviewResponse preview(UUID id, BusinessObjectPreviewRequest request) {
        Instant queriedAt = Instant.now();
        BusinessObjectType type = requireEnabled(id);
        BusinessObjectTypeDefinition definition = definition(type);
        SourceSchema main = requirePreviewMain(type, definition);
        Map<String, Object> mainRow = requireOneMainRow(main, definition, request.objectKey());
        Map<UUID, SourceResult> supplementResults = loadSupplements(main, definition, mainRow);
        Map<UUID, SourceSchema> schemas = sourceSchemas(main, definition);
        List<BusinessObjectPropertyValueResponse> values = definition.properties().stream()
                .sorted(Comparator.comparing(property -> property.sortOrder() == null ? Integer.MAX_VALUE : property.sortOrder()))
                .map(property -> value(property, main, schemas, mainRow, supplementResults, definition))
                .toList();
        String identityCode = main.requireField(definition.mainSource().identityFieldId()).getCode();
        String titleCode = main.requireField(definition.mainSource().titleFieldId()).getCode();
        return new BusinessObjectPreviewResponse(
                identity(type, asKey(mainRow.get(identityCode)), asDisplay(mainRow.get(titleCode))),
                values,
                supplementResults.values().stream().filter(SourceResult::failed)
                        .map(result -> issue("SUPPLEMENT_QUERY_FAILED", result.path(), result.diagnostic(), false)).toList(),
                queriedAt
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BusinessObjectRelatedPreviewResponse previewRelated(
            UUID currentTypeId,
            BusinessObjectRelatedPreviewRequest request
    ) {
        BusinessObjectType current = requireEnabled(currentTypeId);
        List<BusinessObjectType> all = objectTypes.findAll();
        ResolvedRelation resolved = resolveRelation(current, request.relationId(), request.direction(), all);
        SourceSchema currentMain = requirePreviewMain(current, definition(current));
        Map<String, Object> currentRow = requireOneMainRow(currentMain, definition(current), request.objectKey());

        UUID currentFieldId = request.direction() == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND
                ? resolved.relation().sourceFieldId() : resolved.relation().targetFieldId();
        Object relationValue = currentRow.get(currentMain.requireField(currentFieldId).getCode());
        BusinessObjectRelationResponse relation = relationResponse(resolved, request.direction());
        if (relationValue == null) {
            return new BusinessObjectRelatedPreviewResponse(
                    relation, List.of(), page(request.pageNo()), size(request.pageSize()), false,
                    List.of(issue("RELATION_VALUE_EMPTY", "relation", "当前对象的关联字段为空，未关联目标对象", false))
            );
        }

        BusinessObjectType queryType = request.direction() == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND
                ? resolved.target() : resolved.owner();
        if (!queryType.isEnabled()) {
            throw error(HttpStatus.CONFLICT, "关联对象类型已停用");
        }
        BusinessObjectTypeDefinition queryDefinition = definition(queryType);
        SourceSchema queryMain = requirePreviewMain(queryType, queryDefinition);
        UUID queryFieldId = request.direction() == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND
                ? resolved.relation().targetFieldId() : resolved.relation().sourceFieldId();
        DataModelField queryField = queryMain.requireField(queryFieldId);
        List<DataModelDataQueryFilterInput> filters = new ArrayList<>(filters(queryDefinition.mainSource().fixedFilters()));
        filters.add(new DataModelDataQueryFilterInput(queryField.getCode(), "EQ", relationValue, null, List.of()));
        DataModelDataQueryResponse data = query(
                queryMain,
                columns(queryMain, List.of(queryDefinition.mainSource().identityFieldId(), queryDefinition.mainSource().titleFieldId())),
                filters,
                page(request.pageNo()),
                size(request.pageSize())
        );
        String identityCode = queryMain.requireField(queryDefinition.mainSource().identityFieldId()).getCode();
        String titleCode = queryMain.requireField(queryDefinition.mainSource().titleFieldId()).getCode();
        List<BusinessObjectPreviewCandidateResponse> items = data.rows().stream()
                .map(row -> new BusinessObjectPreviewCandidateResponse(asKey(row.get(identityCode)), asDisplay(row.get(titleCode))))
                .toList();
        List<BusinessObjectTypeIssueResponse> diagnostics = cardinalityDiagnostics(resolved.relation(), request.direction(), items.size(), data.hasNext());
        return new BusinessObjectRelatedPreviewResponse(relation, items, data.pageNo(), data.pageSize(), data.hasNext(), diagnostics);
    }

    public BusinessObjectType require(UUID id) {
        return objectTypes.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "业务对象类型不存在"));
    }

    private BusinessObjectType requireEnabled(UUID id) {
        BusinessObjectType type = require(id);
        if (!type.isEnabled()) {
            throw error(HttpStatus.CONFLICT, "业务对象类型已停用");
        }
        return type;
    }

    private BusinessObjectType locked(UUID id) {
        return objectTypes.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "业务对象类型不存在"));
    }

    private BusinessObjectTypeResponse response(BusinessObjectType type, List<BusinessObjectType> all) {
        return response(type, readContext(all, Map.of(type.getId(), definition(type))));
    }

    private BusinessObjectTypeResponse response(BusinessObjectType type, ReadContext context) {
        BusinessObjectTypeDefinition definition = context.definitions.get(type.getId());
        Inspection inspection = inspect(type, definition, context);
        String mainModelName = !canViewModelMetadata() || definition.mainSource() == null || definition.mainSource().modelId() == null
                ? null
                : java.util.Optional.ofNullable(context.schemas.get(definition.mainSource().modelId()))
                        .map(schema -> schema.model().getName()).orElse(null);
        return new BusinessObjectTypeResponse(
                type.getId(), type.getCode(), type.getName(), type.getDirectoryId(), type.getOwnerName(), type.getSummary(),
                type.isEnabled(), mainModelName, definition.properties().size(), context.relationCounts.getOrDefault(type.getId(), 0),
                definition, inspectionResponse(inspection), type.getCreatedAt(), type.getUpdatedAt()
        );
    }

    private BusinessObjectTypeValidationResponse inspectionResponse(Inspection inspection) {
        return new BusinessObjectTypeValidationResponse(
                inspection.issues().stream().noneMatch(BusinessObjectTypeIssueResponse::blocking), inspection.issues()
        );
    }

    private Inspection inspect(
            BusinessObjectType type,
            BusinessObjectTypeDefinition definition,
            List<BusinessObjectType> all
    ) {
        return inspect(type, definition, readContext(all, Map.of(type.getId(), definition)));
    }

    private Inspection inspect(BusinessObjectType type, BusinessObjectTypeDefinition definition, ReadContext context) {
        List<BusinessObjectTypeIssueResponse> issues = new ArrayList<>();
        Map<UUID, SourceSchema> knownSchemas = new HashMap<>();
        MainSource mainSource = definition.mainSource();
        SourceSchema main = mainSource == null ? null : schema(mainSource.modelId(), "mainSource.modelId", true, issues, context.schemas);
        if (main != null) knownSchemas.put(main.model().getId(), main);
        if (mainSource == null || mainSource.modelId() == null) {
            issues.add(issue("MAIN_SOURCE_REQUIRED", "mainSource.modelId", "请先选择已发布的主来源模型", true));
        }
        if (main != null) {
            DataModelField identity = field(main, mainSource.identityFieldId(), "mainSource.identityFieldId", true, issues);
            if (identity != null && !identityType(identity.getFieldType())) {
                issues.add(issue("FIELD_TYPE_UNSUPPORTED", "mainSource.identityFieldId", "对象唯一标识只支持 STRING、BYTE、SHORT、INTEGER 或 LONG 字段", true));
            }
            field(main, mainSource.titleFieldId(), "mainSource.titleFieldId", true, issues);
            validateFilters(main, mainSource.fixedFilters(), "mainSource.fixedFilters", true, issues);
        }

        Set<UUID> supplementIds = new HashSet<>();
        Set<UUID> supplementModels = new HashSet<>();
        if (mainSource != null && mainSource.modelId() != null) supplementModels.add(mainSource.modelId());
        Map<UUID, BusinessObjectTypeDefinition.SupplementSource> supplementsByModel = new HashMap<>();
        for (int index = 0; index < definition.supplements().size(); index++) {
            var supplement = definition.supplements().get(index);
            String path = "supplements[" + index + "]";
            if (supplement.id() == null || !supplementIds.add(supplement.id())) {
                issues.add(issue("DUPLICATE_ID", path + ".id", "补充来源稳定标识不能为空且不能重复", false));
            }
            SourceSchema schema = schema(supplement.modelId(), path + ".modelId", false, issues, context.schemas);
            if (schema != null) knownSchemas.put(schema.model().getId(), schema);
            if (supplement.modelId() != null && !supplementModels.add(supplement.modelId())) {
                issues.add(issue("DUPLICATE_SOURCE_MODEL", path + ".modelId", "同一个模型只能配置一次补充来源", false));
            }
            if (schema != null) {
                supplementsByModel.put(supplement.modelId(), supplement);
                validateFilters(schema, supplement.fixedFilters(), path + ".fixedFilters", false, issues);
                if (supplement.dataTimeFieldId() != null) {
                    field(schema, supplement.dataTimeFieldId(), path + ".dataTimeFieldId", false, issues);
                }
                if (!supplement.keyMappings().isEmpty() && main == null) {
                    issues.add(issue("INVALID_MAPPING", path + ".keyMappings", "请先配置主来源后再设置补充来源匹配字段", false));
                }
                for (int mappingIndex = 0; mappingIndex < supplement.keyMappings().size(); mappingIndex++) {
                    var mapping = supplement.keyMappings().get(mappingIndex);
                    DataModelField mainField = main == null ? null : field(main, mapping.mainFieldId(), path + ".keyMappings[" + mappingIndex + "].mainFieldId", false, issues);
                    DataModelField supplementField = field(schema, mapping.supplementFieldId(), path + ".keyMappings[" + mappingIndex + "].supplementFieldId", false, issues);
                    if (mainField != null && supplementField != null && mainField.getFieldType() != supplementField.getFieldType()) {
                        issues.add(issue("FIELD_TYPE_MISMATCH", path + ".keyMappings[" + mappingIndex + "", "主来源和补充来源匹配字段类型必须相同", false));
                    }
                }
            }
        }

        Set<UUID> groupIds = new HashSet<>();
        for (int index = 0; index < definition.groups().size(); index++) {
            var group = definition.groups().get(index);
            if (group.id() == null || !groupIds.add(group.id())) {
                issues.add(issue("DUPLICATE_ID", "groups[" + index + "].id", "属性分组稳定标识不能为空且不能重复", false));
            }
            if (blank(group.name())) {
                issues.add(issue("GROUP_NAME_REQUIRED", "groups[" + index + "].name", "属性分组名称不能为空", false));
            }
        }

        Set<UUID> propertyIds = new HashSet<>();
        Set<String> propertyCodes = new HashSet<>();
        boolean identityMapped = false;
        boolean titleMapped = false;
        for (int index = 0; index < definition.properties().size(); index++) {
            Property property = definition.properties().get(index);
            String path = "properties[" + index + "]";
            if (property.id() == null || !propertyIds.add(property.id())) {
                issues.add(issue("DUPLICATE_ID", path + ".id", "属性稳定标识不能为空且不能重复", false));
            }
            if (!validCode(property.code()) || !propertyCodes.add(normalizeCode(property.code()))) {
                issues.add(issue("DUPLICATE_CODE", path + ".code", "属性编码必须符合规则且在对象类型内唯一", false));
            }
            if (blank(property.name())) {
                issues.add(issue("PROPERTY_NAME_REQUIRED", path + ".name", "属性名称不能为空", false));
            }
            if (property.groupId() != null && !groupIds.contains(property.groupId())) {
                issues.add(issue("UNKNOWN_GROUP", path + ".groupId", "属性所属分组不存在", false));
            }
            SourceSchema source = property.sourceModelId() == null ? null : knownSchemas.get(property.sourceModelId());
            if (source == null && property.sourceModelId() != null) {
                issues.add(issue("SOURCE_MODEL_NOT_CONFIGURED", path + ".sourceModelId", "属性来源必须是主来源或已配置补充来源", false));
            }
            DataModelField field = source == null ? null : field(source, property.fieldId(), path + ".fieldId", false, issues);
            if (field != null && !queryable(field.getFieldType())) {
                issues.add(issue("FIELD_TYPE_UNSUPPORTED", path + ".fieldId", "BINARY 和 GEOMETRY 字段暂不支持作为业务属性", false));
            }
            if (mainSource != null && Objects.equals(property.sourceModelId(), mainSource.modelId())) {
                identityMapped |= Objects.equals(property.fieldId(), mainSource.identityFieldId());
                titleMapped |= Objects.equals(property.fieldId(), mainSource.titleFieldId());
            }
        }
        if (main != null && !identityMapped) {
            issues.add(issue("IDENTITY_PROPERTY_MISSING", "properties", "对象唯一标识字段应作为一个业务属性加入对象定义", true));
        }
        if (main != null && !titleMapped) {
            issues.add(issue("TITLE_PROPERTY_MISSING", "properties", "对象显示名称字段应作为一个业务属性加入对象定义", true));
        }

        Map<UUID, BusinessObjectType> typesById = context.types;
        Set<UUID> relationIds = new HashSet<>();
        Set<String> relationCodes = new HashSet<>();
        Set<UUID> accessTargets = definition.relations().stream().map(Relation::targetObjectTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        accessTargets.add(type.getId());
        Map<UUID, Set<String>> accessCodesByType = context.accessCodesExcept(type.getId(), accessTargets);
        Set<String> accessCodes = accessCodesByType.computeIfAbsent(type.getId(), ignored -> new HashSet<>());
        for (int index = 0; index < definition.relations().size(); index++) {
            Relation relation = definition.relations().get(index);
            String path = "relations[" + index + "]";
            if (relation.id() == null || !relationIds.add(relation.id())) {
                issues.add(issue("DUPLICATE_ID", path + ".id", "关系稳定标识不能为空且不能重复", false));
            }
            if (relation.id() != null && context.relationOwnedElsewhere(relation.id(), type.getId())) {
                issues.add(issue("DUPLICATE_ID", path + ".id", "关系稳定标识已被其他对象类型使用", false));
            }
            if (!validCode(relation.code()) || !relationCodes.add(normalizeCode(relation.code()))) {
                issues.add(issue("DUPLICATE_CODE", path + ".code", "关系编码必须符合规则且在当前对象类型内唯一", false));
            }
            if (blank(relation.forwardName()) || blank(relation.reverseName())) {
                issues.add(issue("RELATION_NAME_REQUIRED", path, "关系正向和反向名称不能为空", false));
            }
            if (!validCode(relation.forwardAccessCode()) || !accessCodes.add(normalizeCode(relation.forwardAccessCode()))) {
                issues.add(issue("DUPLICATE_ACCESS_CODE", path + ".forwardAccessCode", "当前对象类型内关系访问编码必须符合规则且不能重复", false));
            }
            BusinessObjectType target = relation.targetObjectTypeId() == null ? null : typesById.get(relation.targetObjectTypeId());
            if (relation.targetObjectTypeId() == null) {
                issues.add(issue("RELATION_TARGET_REQUIRED", path + ".targetObjectTypeId", "请选择关系目标对象类型", false));
                continue;
            }
            if (target == null) {
                issues.add(issue("RELATION_TARGET_NOT_FOUND", path + ".targetObjectTypeId", "关系目标对象类型不存在", false));
                continue;
            }
            Set<String> targetAccessCodes = accessCodesByType.computeIfAbsent(target.getId(), ignored -> new HashSet<>());
            if (!validCode(relation.reverseAccessCode()) || !targetAccessCodes.add(normalizeCode(relation.reverseAccessCode()))) {
                issues.add(issue("DUPLICATE_ACCESS_CODE", path + ".reverseAccessCode", "目标对象类型内反向关系访问编码必须符合规则且不能重复", false));
            }
            if (!target.isEnabled()) {
                issues.add(issue("RELATION_TARGET_DISABLED", path + ".targetObjectTypeId", "关系目标对象类型已停用", false));
            }
            BusinessObjectTypeDefinition targetDefinition = Objects.equals(type.getId(), target.getId()) ? definition : context.definitions.get(target.getId());
            SourceSchema targetMain = targetDefinition.mainSource() == null
                    ? null : schema(targetDefinition.mainSource().modelId(), path + ".target", false, issues, context.schemas);
            if (main == null || targetMain == null || targetDefinition.mainSource().identityFieldId() == null) {
                issues.add(issue("RELATION_TARGET_INCOMPLETE", path, "关系双方都必须先配置主来源和对象唯一标识", false));
                continue;
            }
            DataModelField sourceField = field(main, relation.sourceFieldId(), path + ".sourceFieldId", false, issues);
            DataModelField targetField = field(targetMain, relation.targetFieldId(), path + ".targetFieldId", false, issues);
            if (sourceField != null && !hasMainProperty(definition, relation.sourceFieldId())) {
                issues.add(issue("RELATION_PROPERTY_REQUIRED", path + ".sourceFieldId", "关系当前端字段必须先纳入当前对象的主来源属性", false));
            }
            if (targetField != null && !hasMainProperty(targetDefinition, relation.targetFieldId())) {
                issues.add(issue("RELATION_PROPERTY_REQUIRED", path + ".targetFieldId", "关系目标端字段必须先纳入目标对象的主来源属性", false));
            }
            if (sourceField != null && targetField != null && sourceField.getFieldType() != targetField.getFieldType()) {
                issues.add(issue("FIELD_TYPE_MISMATCH", path, "关系两端匹配字段类型必须相同", false));
            }
            if (relation.cardinality() == null) {
                issues.add(issue("RELATION_CARDINALITY_INVALID", path + ".cardinality", "请选择关系数量约束", false));
            } else if (relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.ONE_TO_MANY
                    && !Objects.equals(relation.sourceFieldId(), mainSource.identityFieldId())) {
                issues.add(issue("RELATION_CARDINALITY_INVALID", path + ".sourceFieldId", "一对多关系的当前对象字段必须是当前对象唯一标识", false));
            } else if ((relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.MANY_TO_ONE
                    || relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.ONE_TO_ONE)
                    && !Objects.equals(relation.targetFieldId(), targetDefinition.mainSource().identityFieldId())) {
                issues.add(issue("RELATION_CARDINALITY_INVALID", path + ".targetFieldId", "多对一和一对一关系的目标字段必须是目标对象唯一标识", false));
            }
        }

        Set<UUID> capabilityIds = new HashSet<>();
        Set<String> capabilityCodes = new HashSet<>();
        for (int index = 0; index < definition.capabilities().size(); index++) {
            var capability = definition.capabilities().get(index);
            String path = "capabilities[" + index + "]";
            if (capability.id() == null || !capabilityIds.add(capability.id())) {
                issues.add(issue("DUPLICATE_ID", path + ".id", "能力稳定标识不能为空且不能重复", false));
            }
            if (!validCode(capability.code()) || !capabilityCodes.add(normalizeCode(capability.code()))) {
                issues.add(issue("DUPLICATE_CODE", path + ".code", "能力编码必须符合规则且在对象类型内唯一", false));
            }
            if (blank(capability.name()) || capability.kind() == null) {
                issues.add(issue("CAPABILITY_INCOMPLETE", path, "能力名称和类型不能为空", false));
            }
        }
        return new Inspection(List.copyOf(issues));
    }

    private SourceSchema requirePreviewMain(BusinessObjectType type, BusinessObjectTypeDefinition definition) {
        Inspection inspection = inspect(type, definition, objectTypes.findAll());
        if (inspection.issues().stream().anyMatch(BusinessObjectTypeIssueResponse::blocking)) {
            String message = inspection.issues().stream().filter(BusinessObjectTypeIssueResponse::blocking)
                    .map(BusinessObjectTypeIssueResponse::message).findFirst().orElse("对象主来源配置不完整");
            throw error(HttpStatus.CONFLICT, message);
        }
        return requireSchema(definition.mainSource().modelId());
    }

    private Map<String, Object> requireOneMainRow(
            SourceSchema main,
            BusinessObjectTypeDefinition definition,
            String objectKey
    ) {
        MainSource source = definition.mainSource();
        DataModelField identity = main.requireField(source.identityFieldId());
        List<DataModelDataQueryFilterInput> filters = new ArrayList<>(filters(source.fixedFilters()));
        filters.add(new DataModelDataQueryFilterInput(identity.getCode(), "EQ", objectKey, null, List.of()));
        DataModelDataQueryResponse data = query(main, columns(main, allMainColumns(definition)), filters, 1, 2);
        if (data.rows().isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "未找到对应业务对象");
        }
        if (data.rows().size() > 1 || data.hasNext()) {
            throw error(HttpStatus.CONFLICT, "对象唯一标识匹配到多条主来源记录");
        }
        return data.rows().getFirst();
    }

    private List<UUID> allMainColumns(BusinessObjectTypeDefinition definition) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        MainSource source = definition.mainSource();
        if (source != null) {
            result.add(source.identityFieldId());
            result.add(source.titleFieldId());
        }
        definition.properties().stream()
                .filter(property -> source != null && Objects.equals(property.sourceModelId(), source.modelId()))
                .map(Property::fieldId).forEach(result::add);
        definition.supplements().forEach(supplement -> supplement.keyMappings().stream()
                .map(BusinessObjectTypeDefinition.FieldMapping::mainFieldId).forEach(result::add));
        return result.stream().filter(Objects::nonNull).toList();
    }

    private Map<UUID, SourceResult> loadSupplements(
            SourceSchema main,
            BusinessObjectTypeDefinition definition,
            Map<String, Object> mainRow
    ) {
        Map<UUID, SourceResult> result = new HashMap<>();
        Map<UUID, List<Property>> propertiesByModel = definition.properties().stream()
                .filter(property -> property.sourceModelId() != null)
                .filter(property -> !Objects.equals(property.sourceModelId(), definition.mainSource().modelId()))
                .collect(Collectors.groupingBy(Property::sourceModelId));
        for (int index = 0; index < definition.supplements().size(); index++) {
            var supplement = definition.supplements().get(index);
            if (supplement.id() == null || supplement.modelId() == null) {
                continue;
            }
            String path = "supplements[" + index + "]";
            SourceSchema schema;
            try {
                schema = requireSchema(supplement.modelId());
                if (supplement.keyMappings().isEmpty()) {
                    result.put(supplement.modelId(), SourceResult.error(path, "补充来源未配置匹配字段"));
                    continue;
                }
                List<DataModelDataQueryFilterInput> filters = new ArrayList<>(filters(supplement.fixedFilters()));
                boolean nullKey = false;
                for (var mapping : supplement.keyMappings()) {
                    DataModelField mainField = main.requireField(mapping.mainFieldId());
                    Object value = mainRow.get(mainField.getCode());
                    if (value == null) {
                        nullKey = true;
                        break;
                    }
                    filters.add(new DataModelDataQueryFilterInput(
                            schema.requireField(mapping.supplementFieldId()).getCode(), "EQ", value, null, List.of()
                    ));
                }
                if (nullKey) {
                    result.put(supplement.modelId(), SourceResult.noMatch(path, "关联键为空，未查询补充来源"));
                    continue;
                }
                LinkedHashSet<UUID> fields = new LinkedHashSet<>();
                propertiesByModel.getOrDefault(supplement.modelId(), List.of()).forEach(property -> fields.add(property.fieldId()));
                supplement.keyMappings().forEach(mapping -> fields.add(mapping.supplementFieldId()));
                if (supplement.dataTimeFieldId() != null) {
                    fields.add(supplement.dataTimeFieldId());
                }
                DataModelDataQueryResponse data = query(schema, columns(schema, fields), filters, 1, 2);
                if (data.rows().isEmpty()) {
                    result.put(supplement.modelId(), SourceResult.noMatch(path, "补充来源未匹配到记录"));
                } else if (data.rows().size() > 1 || data.hasNext()) {
                    result.put(supplement.modelId(), SourceResult.error(path, "补充来源匹配到多条记录"));
                } else {
                    Object time = supplement.dataTimeFieldId() == null ? null
                            : data.rows().getFirst().get(schema.requireField(supplement.dataTimeFieldId()).getCode());
                    result.put(supplement.modelId(), SourceResult.matched(path, data.rows().getFirst(), time));
                }
            } catch (ResponseStatusException exception) {
                result.put(supplement.modelId(), SourceResult.error(path, exception.getReason()));
            } catch (CodedProblemException exception) {
                result.put(supplement.modelId(), SourceResult.error(path, "补充来源读取失败，请检查来源状态和物理结构"));
            }
        }
        return result;
    }

    private BusinessObjectPropertyValueResponse value(
            Property property,
            SourceSchema main,
            Map<UUID, SourceSchema> schemas,
            Map<String, Object> mainRow,
            Map<UUID, SourceResult> supplementResults,
            BusinessObjectTypeDefinition definition
    ) {
        SourceSchema schema = schemas.get(property.sourceModelId());
        if (schema == null || property.fieldId() == null || schema.field(property.fieldId()) == null) {
            return new BusinessObjectPropertyValueResponse(
                    property.id(), property.code(), property.name(), property.groupId(), property.unit(), null, null,
                    "ERROR", property.sourceModelId(), null, property.fieldId(), null, null, null, "属性来源配置无效"
            );
        }
        DataModelField field = schema.requireField(property.fieldId());
        if (Objects.equals(property.sourceModelId(), definition.mainSource().modelId())) {
            Object value = mainRow.get(field.getCode());
            return propertyValue(property, schema, field, value, value == null ? "NULL" : "MATCHED", null, null);
        }
        SourceResult source = supplementResults.get(property.sourceModelId());
        if (source == null || source.row() == null) {
            String status = source == null ? "ERROR" : source.status();
            return propertyValue(property, schema, field, null, status, source == null ? null : source.dataTime(),
                    source == null ? "补充来源未配置" : source.diagnostic());
        }
        Object value = source.row().get(field.getCode());
        return propertyValue(property, schema, field, value, value == null ? "NULL" : "MATCHED", source.dataTime(), source.diagnostic());
    }

    private BusinessObjectPropertyValueResponse propertyValue(
            Property property,
            SourceSchema schema,
            DataModelField field,
            Object value,
            String status,
            Object dataTime,
            String diagnostic
    ) {
        return new BusinessObjectPropertyValueResponse(
                property.id(), property.code(), property.name(), property.groupId(), property.unit(), field.getFieldType().name(),
                value, status, schema.model().getId(), schema.model().getName(), field.getId(), field.getCode(), field.getName(),
                dataTime, diagnostic
        );
    }

    private ResolvedRelation resolveRelation(
            BusinessObjectType current,
            UUID relationId,
            BusinessObjectRelatedPreviewRequest.Direction direction,
            List<BusinessObjectType> all
    ) {
        if (direction == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND) {
            Relation relation = definition(current).relations().stream()
                    .filter(candidate -> Objects.equals(candidate.id(), relationId)).findFirst()
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "当前对象类型没有该关系"));
            BusinessObjectType target = all.stream().filter(type -> Objects.equals(type.getId(), relation.targetObjectTypeId()))
                    .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "关系目标对象类型不存在"));
            return new ResolvedRelation(current, target, relation);
        }
        for (BusinessObjectType owner : all) {
            Relation relation = definition(owner).relations().stream()
                    .filter(candidate -> Objects.equals(candidate.id(), relationId)
                            && Objects.equals(candidate.targetObjectTypeId(), current.getId()))
                    .findFirst().orElse(null);
            if (relation != null) {
                return new ResolvedRelation(owner, current, relation);
            }
        }
        throw error(HttpStatus.NOT_FOUND, "当前对象类型没有该反向关系");
    }

    private List<BusinessObjectRelationResponse> availableRelations(UUID typeId, List<BusinessObjectType> all) {
        Map<UUID, BusinessObjectType> types = all.stream()
                .collect(Collectors.toMap(BusinessObjectType::getId, Function.identity()));
        List<BusinessObjectRelationResponse> result = new ArrayList<>();
        BusinessObjectType current = types.get(typeId);
        if (current == null) {
            return List.of();
        }
        for (Relation relation : definition(current).relations()) {
            BusinessObjectType target = types.get(relation.targetObjectTypeId());
            if (target != null) {
                result.add(relationResponse(new ResolvedRelation(current, target, relation), BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND));
            }
        }
        for (BusinessObjectType owner : all) {
            for (Relation relation : definition(owner).relations()) {
                if (Objects.equals(relation.targetObjectTypeId(), typeId)) {
                    result.add(relationResponse(new ResolvedRelation(owner, current, relation), BusinessObjectRelatedPreviewRequest.Direction.INBOUND));
                }
            }
        }
        return result.stream().sorted(Comparator.comparing(BusinessObjectRelationResponse::name, Comparator.nullsLast(String::compareTo))).toList();
    }

    private BusinessObjectRelationResponse relationResponse(
            ResolvedRelation resolved,
            BusinessObjectRelatedPreviewRequest.Direction direction
    ) {
        Relation relation = resolved.relation();
        boolean outbound = direction == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND;
        return new BusinessObjectRelationResponse(
                relation.id(), relation.code(), outbound ? relation.forwardName() : relation.reverseName(),
                outbound ? relation.forwardAccessCode() : relation.reverseAccessCode(),
                outbound ? relation.reverseName() : relation.forwardName(),
                outbound ? relation.reverseAccessCode() : relation.forwardAccessCode(), relation.description(), relation.cardinality(), direction.name(),
                resolved.owner().getId(), resolved.owner().getName(), resolved.target().getId(), resolved.target().getName()
        );
    }

    private List<BusinessObjectTypeIssueResponse> cardinalityDiagnostics(
            Relation relation,
            BusinessObjectRelatedPreviewRequest.Direction direction,
            int itemCount,
            boolean hasNext
    ) {
        boolean expectsOne = relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.ONE_TO_ONE
                || (relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.MANY_TO_ONE
                && direction == BusinessObjectRelatedPreviewRequest.Direction.OUTBOUND)
                || (relation.cardinality() == BusinessObjectTypeDefinition.RelationCardinality.ONE_TO_MANY
                && direction == BusinessObjectRelatedPreviewRequest.Direction.INBOUND);
        if (expectsOne && (itemCount > 1 || hasNext)) {
            return List.of(issue("RELATION_CARDINALITY_VIOLATION", "relation", "当前读取发现单值关系匹配多个对象", false));
        }
        if (itemCount == 0 && !hasNext) {
            return List.of(issue("RELATION_TARGET_MISSING", "relation", "本页未找到对应目标对象，请检查关联编码和固定筛选", false));
        }
        return List.of();
    }

    private Map<UUID, SourceSchema> sourceSchemas(SourceSchema main, BusinessObjectTypeDefinition definition) {
        Map<UUID, SourceSchema> schemas = new HashMap<>();
        schemas.put(main.model().getId(), main);
        definition.supplements().stream().map(BusinessObjectTypeDefinition.SupplementSource::modelId)
                .filter(Objects::nonNull).distinct().forEach(modelId -> {
                    try {
                        schemas.put(modelId, requireSchema(modelId));
                    } catch (ResponseStatusException ignored) {
                        // A failed supplement must leave usable main-source properties available in preview.
                    }
                });
        return schemas;
    }

    private SourceSchema requireSchema(UUID modelId) {
        DataModel model = models.findById(modelId).orElseThrow(() -> error(HttpStatus.CONFLICT, "来源模型不存在"));
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw error(HttpStatus.CONFLICT, "来源模型必须处于已发布状态");
        }
        return new SourceSchema(model, fields.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId));
    }

    private SourceSchema schema(
            UUID modelId,
            String path,
            boolean blocking,
            List<BusinessObjectTypeIssueResponse> issues,
            Map<UUID, SourceSchema> cache
    ) {
        if (modelId == null) {
            return null;
        }
        SourceSchema loaded = cache.get(modelId);
        DataModel model = loaded == null ? null : loaded.model();
        if (model == null) {
            issues.add(issue("MODEL_NOT_FOUND", path, "引用的数据模型不存在", blocking));
            return null;
        }
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            issues.add(issue("MODEL_NOT_PUBLISHED", path, "来源模型必须处于已发布状态", blocking));
            return null;
        }
        return loaded;
    }

    private DataModelField field(
            SourceSchema schema,
            UUID id,
            String path,
            boolean blocking,
            List<BusinessObjectTypeIssueResponse> issues
    ) {
        if (id == null) {
            issues.add(issue("FIELD_REQUIRED", path, "请选择来源字段", blocking));
            return null;
        }
        DataModelField field = schema.field(id);
        if (field == null) {
            issues.add(issue("FIELD_NOT_FOUND", path, "字段不存在或不属于所选模型", blocking));
        }
        return field;
    }

    private void validateFilters(
            SourceSchema schema,
            List<Filter> filters,
            String path,
            boolean blocking,
            List<BusinessObjectTypeIssueResponse> issues
    ) {
        for (int index = 0; index < filters.size(); index++) {
            Filter filter = filters.get(index);
            String itemPath = path + "[" + index + "]";
            field(schema, filter.fieldId(), itemPath + ".fieldId", blocking, issues);
            if (blank(filter.operator())) {
                issues.add(issue("FILTER_OPERATOR_REQUIRED", itemPath + ".operator", "筛选条件必须选择运算符", blocking));
            }
        }
    }

    private DataModelDataQueryResponse query(
            SourceSchema schema,
            List<String> columnCodes,
            List<DataModelDataQueryFilterInput> filters,
            int pageNo,
            int pageSize
    ) {
        return modelData.queryPhysicalTableData(
                schema.model().getId(),
                new DataModelDataQueryRequest(pageNo, pageSize, "AND", columnCodes, filters, List.of(), false)
        );
    }

    private List<String> columns(SourceSchema schema, Collection<UUID> fieldIds) {
        return fieldIds.stream().filter(Objects::nonNull).map(schema::requireField)
                .map(DataModelField::getCode).distinct().toList();
    }

    private List<DataModelDataQueryFilterInput> filters(List<Filter> filters) {
        return filters.stream().map(filter -> new DataModelDataQueryFilterInput(
                fieldById(filter.fieldId()).getCode(), filter.operator(), filter.value(), filter.secondValue(), filter.values()
        )).toList();
    }

    private DataModelField fieldById(UUID fieldId) {
        return fields.findById(fieldId).orElseThrow(() -> error(HttpStatus.CONFLICT, "来源字段不存在"));
    }

    private void replaceReferences(UUID objectTypeId, BusinessObjectTypeDefinition definition) {
        List<BusinessObjectTypeReference> projection = new ArrayList<>();
        if (definition.mainSource() != null) {
            addReference(projection, objectTypeId, "mainSource.modelId", BusinessObjectTypeReferenceKind.MODEL, definition.mainSource().modelId());
            addReference(projection, objectTypeId, "mainSource.identityFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, definition.mainSource().identityFieldId());
            addReference(projection, objectTypeId, "mainSource.titleFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, definition.mainSource().titleFieldId());
            addFilterReferences(projection, objectTypeId, "mainSource.fixedFilters", definition.mainSource().fixedFilters());
        }
        for (int index = 0; index < definition.supplements().size(); index++) {
            var source = definition.supplements().get(index);
            String path = "supplements[" + index + "]";
            addReference(projection, objectTypeId, path + ".modelId", BusinessObjectTypeReferenceKind.MODEL, source.modelId());
            addReference(projection, objectTypeId, path + ".dataTimeFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, source.dataTimeFieldId());
            for (int mapping = 0; mapping < source.keyMappings().size(); mapping++) {
                var item = source.keyMappings().get(mapping);
                addReference(projection, objectTypeId, path + ".keyMappings[" + mapping + "].mainFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, item.mainFieldId());
                addReference(projection, objectTypeId, path + ".keyMappings[" + mapping + "].supplementFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, item.supplementFieldId());
            }
            addFilterReferences(projection, objectTypeId, path + ".fixedFilters", source.fixedFilters());
        }
        for (int index = 0; index < definition.properties().size(); index++) {
            Property property = definition.properties().get(index);
            addReference(projection, objectTypeId, "properties[" + index + "].sourceModelId", BusinessObjectTypeReferenceKind.MODEL, property.sourceModelId());
            addReference(projection, objectTypeId, "properties[" + index + "].fieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, property.fieldId());
        }
        for (int index = 0; index < definition.relations().size(); index++) {
            Relation relation = definition.relations().get(index);
            String path = "relations[" + index + "]";
            addReference(projection, objectTypeId, path + ".targetObjectTypeId", BusinessObjectTypeReferenceKind.OBJECT_TYPE, relation.targetObjectTypeId());
            addReference(projection, objectTypeId, path + ".sourceFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, relation.sourceFieldId());
            addReference(projection, objectTypeId, path + ".targetFieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, relation.targetFieldId());
        }
        references.deleteAllByObjectTypeId(objectTypeId);
        references.flush();
        references.saveAll(projection);
    }

    private void addFilterReferences(
            List<BusinessObjectTypeReference> projection,
            UUID objectTypeId,
            String path,
            List<Filter> filters
    ) {
        for (int index = 0; index < filters.size(); index++) {
            addReference(projection, objectTypeId, path + "[" + index + "].fieldId", BusinessObjectTypeReferenceKind.MODEL_FIELD, filters.get(index).fieldId());
        }
    }

    private void addReference(
            List<BusinessObjectTypeReference> projection,
            UUID objectTypeId,
            String path,
            BusinessObjectTypeReferenceKind kind,
            UUID resourceId
    ) {
        if (resourceId != null) {
            projection.add(BusinessObjectTypeReference.create(objectTypeId, path, kind, resourceId));
        }
    }

    private void assertIdentityCanChange(BusinessObjectType type, BusinessObjectTypeDefinition updated) {
        BusinessObjectTypeDefinition existing = definition(type);
        UUID currentIdentity = existing.mainSource() == null ? null : existing.mainSource().identityFieldId();
        UUID nextIdentity = updated.mainSource() == null ? null : updated.mainSource().identityFieldId();
        UUID currentModel = existing.mainSource() == null ? null : existing.mainSource().modelId();
        UUID nextModel = updated.mainSource() == null ? null : updated.mainSource().modelId();
        if (Objects.equals(currentIdentity, nextIdentity) && Objects.equals(currentModel, nextModel)) {
            return;
        }
        if (!references.findAllByReferenceKindAndResourceId(
                BusinessObjectTypeReferenceKind.OBJECT_TYPE, type.getId()).isEmpty()) {
            throw new CodedProblemException(
                    HttpStatus.CONFLICT,
                    "OBJECT_TYPE_REFERENCED",
                    "当前对象类型被其他业务关系引用，不能直接变更主来源或对象唯一标识"
            );
        }
    }

    private boolean hasMainProperty(BusinessObjectTypeDefinition definition, UUID fieldId) {
        return definition.mainSource() != null && definition.properties().stream().anyMatch(property ->
                Objects.equals(property.sourceModelId(), definition.mainSource().modelId())
                        && Objects.equals(property.fieldId(), fieldId));
    }

    private void assertReferencedPropertiesCanChange(BusinessObjectType type, BusinessObjectTypeDefinition updated,
                                                    List<BusinessObjectType> all) {
        BusinessObjectTypeDefinition existing = definition(type);
        for (BusinessObjectType owner : all) {
            if (Objects.equals(owner.getId(), type.getId())) continue;
            for (Relation relation : definition(owner).relations()) {
                if (!Objects.equals(relation.targetObjectTypeId(), type.getId())) continue;
                for (Property property : existing.properties()) {
                    if (existing.mainSource() == null
                            || !Objects.equals(property.sourceModelId(), existing.mainSource().modelId())
                            || !Objects.equals(property.fieldId(), relation.targetFieldId())) continue;
                    boolean retained = updated.properties().stream().anyMatch(next -> Objects.equals(next.id(), property.id())
                            && Objects.equals(next.sourceModelId(), property.sourceModelId())
                            && Objects.equals(next.fieldId(), property.fieldId()));
                    if (!retained) throw error(HttpStatus.CONFLICT, "属性仍被其他对象类型的业务关系引用，请先解除关系再删除或改绑属性");
                }
            }
        }
    }

    private void assertNoInboundRelations(UUID id) {
        if (!references.findAllByReferenceKindAndResourceId(BusinessObjectTypeReferenceKind.OBJECT_TYPE, id).isEmpty()) {
            throw new CodedProblemException(
                    HttpStatus.CONFLICT,
                    "OBJECT_TYPE_REFERENCED",
                    "对象类型仍被其他业务关系引用，请先解除关系"
            );
        }
    }

    private BusinessObjectTypeDefinition definition(BusinessObjectType type) {
        try {
            return mapper.readValue(type.getDefinition(), BusinessObjectTypeDefinition.class);
        } catch (Exception exception) {
            throw new IllegalStateException("业务对象定义无法读取", exception);
        }
    }

    private String json(BusinessObjectTypeDefinition definition) {
        try {
            return mapper.writeValueAsString(definition);
        } catch (Exception exception) {
            throw new IllegalStateException("业务对象定义无法保存", exception);
        }
    }

    private static BusinessObjectIdentityResponse identity(BusinessObjectType type, String key, String title) {
        return new BusinessObjectIdentityResponse(type.getId(), type.getCode(), type.getName(), key, title);
    }

    private static BusinessObjectTypeIssueResponse issue(String code, String path, String message, boolean blocking) {
        return new BusinessObjectTypeIssueResponse(code, path, message, blocking);
    }

    private static int page(Integer pageNo) {
        return pageNo == null ? 1 : pageNo;
    }

    private static int size(Integer pageSize) {
        return pageSize == null ? 20 : pageSize;
    }

    private static String asKey(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String asDisplay(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static boolean identityType(PlatformDataType type) {
        return type == PlatformDataType.STRING || type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG;
    }

    private static boolean queryable(PlatformDataType type) {
        return type != PlatformDataType.BINARY && type != PlatformDataType.GEOMETRY;
    }

    private static boolean canViewModelMetadata() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "model.view".equals(authority.getAuthority()));
    }

    private static boolean validCode(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]{0,63}");
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requiredCode(String value, String label) {
        if (!validCode(value)) {
            throw error(HttpStatus.BAD_REQUEST, label + "必须匹配 [a-z][a-z0-9_]{0,63}");
        }
        return normalizeCode(value);
    }

    private static String requiredText(String value, String label) {
        if (blank(value)) {
            throw error(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return blank(value) ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    /** One request snapshot: deserialize and index each definition once, batch only relevant model metadata. */
    private ReadContext readContext(List<BusinessObjectType> all, Map<UUID, BusinessObjectTypeDefinition> inspected) {
        ReadContext context = new ReadContext();
        for (BusinessObjectType type : all) {
            context.types.put(type.getId(), type);
            context.definitions.put(type.getId(), inspected.containsKey(type.getId())
                    ? inspected.get(type.getId()) : definition(type));
        }
        for (var entry : context.definitions.entrySet()) {
            UUID owner = entry.getKey();
            for (Relation relation : entry.getValue().relations()) {
                if (relation.id() != null) {
                    context.relationOwners.computeIfAbsent(relation.id(), ignored -> new HashSet<>()).add(owner);
                }
                context.addAccessCode(owner, relation.forwardAccessCode(), owner);
                if (relation.targetObjectTypeId() != null) {
                    context.addAccessCode(relation.targetObjectTypeId(), relation.reverseAccessCode(), owner);
                }
                if (context.types.containsKey(relation.targetObjectTypeId())) {
                    context.relationCounts.merge(owner, 1, Integer::sum);
                    context.relationCounts.merge(relation.targetObjectTypeId(), 1, Integer::sum);
                }
            }
        }
        Set<UUID> modelIds = new HashSet<>();
        for (BusinessObjectTypeDefinition definition : inspected.values()) {
            if (definition.mainSource() != null) modelIds.add(definition.mainSource().modelId());
            definition.supplements().forEach(source -> modelIds.add(source.modelId()));
            for (Relation relation : definition.relations()) {
                BusinessObjectTypeDefinition target = context.definitions.get(relation.targetObjectTypeId());
                if (target != null && target.mainSource() != null) modelIds.add(target.mainSource().modelId());
            }
        }
        modelIds.remove(null);
        if (!modelIds.isEmpty()) {
            Map<UUID, List<DataModelField>> modelFields = fields.findAllByModelIdInOrderByModelAndSort(modelIds).stream()
                    .collect(Collectors.groupingBy(DataModelField::getModelId));
            models.findAllById(modelIds).forEach(model -> context.schemas.put(model.getId(),
                    new SourceSchema(model, modelFields.getOrDefault(model.getId(), List.of()))));
        }
        return context;
    }

    private static final class ReadContext {
        final Map<UUID, BusinessObjectType> types = new HashMap<>();
        final Map<UUID, BusinessObjectTypeDefinition> definitions = new HashMap<>();
        final Map<UUID, SourceSchema> schemas = new HashMap<>();
        final Map<UUID, Set<UUID>> relationOwners = new HashMap<>();
        final Map<UUID, Integer> relationCounts = new HashMap<>();
        final Map<UUID, Map<String, Set<UUID>>> accessOwners = new HashMap<>();

        boolean relationOwnedElsewhere(UUID relationId, UUID owner) {
            return relationOwners.getOrDefault(relationId, Set.of()).stream().anyMatch(id -> !id.equals(owner));
        }

        void addAccessCode(UUID target, String code, UUID owner) {
            if (!validCode(code)) return;
            accessOwners.computeIfAbsent(target, ignored -> new HashMap<>())
                    .computeIfAbsent(normalizeCode(code), ignored -> new HashSet<>()).add(owner);
        }

        Map<UUID, Set<String>> accessCodesExcept(UUID owner, Set<UUID> targets) {
            Map<UUID, Set<String>> result = new HashMap<>();
            for (UUID target : targets) {
                Set<String> codes = new HashSet<>();
                accessOwners.getOrDefault(target, Map.of()).forEach((code, owners) -> {
                    if (owners.stream().anyMatch(id -> !id.equals(owner))) codes.add(code);
                });
                result.put(target, codes);
            }
            return result;
        }
    }

    private record Inspection(List<BusinessObjectTypeIssueResponse> issues) {
    }

    private record SourceSchema(DataModel model, Map<UUID, DataModelField> fieldsById) {
        SourceSchema(DataModel model, List<DataModelField> fields) {
            this(model, fields.stream().collect(Collectors.toMap(DataModelField::getId, Function.identity())));
        }

        DataModelField field(UUID id) {
            return id == null ? null : fieldsById.get(id);
        }

        DataModelField requireField(UUID id) {
            DataModelField field = field(id);
            if (field == null) {
                throw error(HttpStatus.CONFLICT, "字段不存在或不属于当前来源模型");
            }
            return field;
        }
    }

    private record SourceResult(String path, Map<String, Object> row, String status, Object dataTime, String diagnostic) {
        static SourceResult matched(String path, Map<String, Object> row, Object dataTime) {
            return new SourceResult(path, row, "MATCHED", dataTime, null);
        }

        static SourceResult noMatch(String path, String diagnostic) {
            return new SourceResult(path, null, "NO_MATCH", null, diagnostic);
        }

        static SourceResult error(String path, String diagnostic) {
            return new SourceResult(path, null, "ERROR", null, diagnostic);
        }

        boolean failed() {
            return "ERROR".equals(status);
        }
    }

    private record ResolvedRelation(BusinessObjectType owner, BusinessObjectType target, Relation relation) {
    }
}
