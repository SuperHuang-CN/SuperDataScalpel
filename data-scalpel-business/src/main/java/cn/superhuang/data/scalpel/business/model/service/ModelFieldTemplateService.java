package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplate;
import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplateItem;
import cn.superhuang.data.scalpel.business.model.repository.ModelFieldTemplateItemRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelFieldTemplateRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ModelFieldTemplateFieldInput;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelFieldTemplateFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelFieldTemplateResponse;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelFieldTemplateService {

    private final ModelFieldTemplateRepository repository;
    private final ModelFieldTemplateItemRepository itemRepository;
    private final StandardDictionaryValueSupport standardDictionaryValueSupport;
    private final SearchEngine searchEngine;

    public ModelFieldTemplateService(
            ModelFieldTemplateRepository repository,
            ModelFieldTemplateItemRepository itemRepository,
            StandardDictionaryValueSupport standardDictionaryValueSupport,
            SearchEngine searchEngine
    ) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.standardDictionaryValueSupport = standardDictionaryValueSupport;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModelFieldTemplateResponse> search(SearchRequest request) {
        SearchRequest effective = request == null
                ? new SearchRequest(null, 0, 20, "category,sortOrder,name,code")
                : new SearchRequest(
                        request.search(),
                        request.page(),
                        request.size(),
                        request.sort() == null || request.sort().isBlank()
                                ? "category,sortOrder,name,code"
                                : request.sort()
                );
        Page<ModelFieldTemplate> page = searchEngine.search(
                effective,
                ModelFieldTemplate.class,
                repository
        );
        Map<UUID, ModelFieldTemplateResponse> responses = responses(page.getContent());
        return new PageResponse<>(
                page.getContent().stream().map(template -> responses.get(template.getId())).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ModelFieldTemplateResponse get(UUID id) {
        return response(requireTemplate(id));
    }

    @Transactional
    public ModelFieldTemplateResponse create(CreateModelFieldTemplateRequest request) {
        String code = normalizeTemplateCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "常用字段模板编码已存在：" + code);
        }
        List<PreparedField> preparedFields = prepareFields(request.fields(), Map.of());
        ModelFieldTemplate template = ModelFieldTemplate.create(
                code, request.name(), request.category(), request.description(), request.sortOrder()
        );
        repository.saveAndFlush(template);
        saveFields(template.getId(), preparedFields, Map.of());
        return response(template);
    }

    @Transactional
    public ModelFieldTemplateResponse update(UUID id, UpdateModelFieldTemplateRequest request) {
        ModelFieldTemplate template = requireTemplateForUpdate(id);
        requireVersion(template, request.expectedVersion());
        String code = normalizeTemplateCode(request.code());
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "常用字段模板编码已存在：" + code);
        }
        Map<UUID, ModelFieldTemplateItem> currentFields = itemRepository
                .findAllByTemplateIdOrderBySortOrderAscCodeAsc(id).stream()
                .collect(Collectors.toMap(ModelFieldTemplateItem::getId, Function.identity()));
        List<PreparedField> preparedFields = prepareFields(request.fields(), currentFields);
        template.update(code, request.name(), request.category(), request.description(), request.sortOrder());
        template.advanceVersion();
        repository.saveAndFlush(template);
        saveFields(id, preparedFields, currentFields);
        return response(template);
    }

    @Transactional
    public ModelFieldTemplateResponse enable(UUID id) {
        ModelFieldTemplate template = requireTemplateForUpdate(id);
        if (template.enable()) {
            template.advanceVersion();
            repository.saveAndFlush(template);
        }
        return response(template);
    }

    @Transactional
    public ModelFieldTemplateResponse disable(UUID id) {
        ModelFieldTemplate template = requireTemplateForUpdate(id);
        if (template.disable()) {
            template.advanceVersion();
            repository.saveAndFlush(template);
        }
        return response(template);
    }

    @Transactional
    public void delete(UUID id) {
        ModelFieldTemplate template = requireTemplateForUpdate(id);
        itemRepository.deleteAllByTemplateId(id);
        itemRepository.flush();
        repository.delete(template);
        repository.flush();
    }

    private List<PreparedField> prepareFields(
            List<ModelFieldTemplateFieldInput> fields,
            Map<UUID, ModelFieldTemplateItem> currentFields
    ) {
        Set<String> codes = new HashSet<>();
        Set<UUID> fieldIds = new HashSet<>();
        List<PreparedField> prepared = new ArrayList<>();
        for (ModelFieldTemplateFieldInput input : fields) {
            String code = input.code().trim().toLowerCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板字段编码不能重复：" + code);
            }
            ModelFieldTemplateItem current = null;
            if (input.id() != null) {
                current = currentFields.get(input.id());
                if (current == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板字段不存在或不属于当前模板");
                }
                if (!fieldIds.add(input.id())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板字段 ID 不能重复");
                }
            }
            if (input.primaryKey() && input.nullable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主键字段不能为空：" + code);
            }

            Integer length = null;
            Integer precision = null;
            Integer scale = null;
            GeometryTypeDefinition geometry = null;
            if (input.fieldType() == PlatformDataType.STRING) {
                if (input.geometry() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
                }
                length = input.length();
            } else if (input.fieldType() == PlatformDataType.DECIMAL) {
                if (input.geometry() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
                }
                if (input.precision() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段必须指定精度：" + code);
                }
                precision = input.precision();
                scale = input.scale() == null ? 0 : input.scale();
                if (scale > precision) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段的小数位不能超过精度：" + code);
                }
            } else if (input.fieldType() == PlatformDataType.GEOMETRY) {
                if (input.geometry() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Geometry 字段必须指定几何类型、CRS 和坐标维度：" + code
                    );
                }
                if (input.length() != null || input.precision() != null || input.scale() != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Geometry 字段不能设置长度、精度或小数位：" + code
                    );
                }
                if (!"EPSG".equals(input.geometry().crs().authority())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段第一版只支持 EPSG CRS：" + code);
                }
                if (input.geometry().dimension() != CoordinateDimension.XY) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段第一版只支持 XY 二维坐标：" + code);
                }
                if (input.primaryKey()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段不能作为主键：" + code);
                }
                geometry = input.geometry();
            } else if (input.geometry() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 Geometry 字段不能设置空间参数：" + code);
            }

            standardDictionaryValueSupport.validateAssignment(
                    input.standardDictionaryId(),
                    current == null ? null : current.getStandardDictionaryId(),
                    input.fieldType(), length, precision, scale
            );
            prepared.add(new PreparedField(input, code, length, precision, scale, geometry));
        }
        return prepared;
    }

    private void saveFields(
            UUID templateId,
            List<PreparedField> preparedFields,
            Map<UUID, ModelFieldTemplateItem> currentFields
    ) {
        Set<UUID> retainedIds = new HashSet<>();
        List<ModelFieldTemplateItem> fieldsToSave = new ArrayList<>();
        for (PreparedField prepared : preparedFields) {
            ModelFieldTemplateFieldInput input = prepared.input();
            ModelFieldTemplateItem item;
            if (input.id() == null) {
                item = ModelFieldTemplateItem.create(
                        templateId, prepared.code(), input.name(), input.fieldType(),
                        prepared.length(), prepared.precision(), prepared.scale(), prepared.geometry(),
                        input.nullable(), input.primaryKey(), input.sortOrder(), input.description(),
                        input.standardDictionaryId()
                );
            } else {
                item = currentFields.get(input.id());
                retainedIds.add(input.id());
                item.update(
                        prepared.code(), input.name(), input.fieldType(),
                        prepared.length(), prepared.precision(), prepared.scale(), prepared.geometry(),
                        input.nullable(), input.primaryKey(), input.sortOrder(), input.description(),
                        input.standardDictionaryId()
                );
            }
            fieldsToSave.add(item);
        }
        List<ModelFieldTemplateItem> removed = currentFields.values().stream()
                .filter(item -> !retainedIds.contains(item.getId()))
                .toList();
        if (!removed.isEmpty()) {
            itemRepository.deleteAll(removed);
            itemRepository.flush();
        }
        itemRepository.saveAllAndFlush(fieldsToSave);
    }

    private ModelFieldTemplateResponse response(ModelFieldTemplate template) {
        return responses(List.of(template)).get(template.getId());
    }

    private Map<UUID, ModelFieldTemplateResponse> responses(List<ModelFieldTemplate> templates) {
        if (templates.isEmpty()) {
            return Map.of();
        }
        List<ModelFieldTemplateItem> items = itemRepository
                .findAllByTemplateIdInOrderByTemplateIdAscSortOrderAscCodeAsc(
                        templates.stream().map(ModelFieldTemplate::getId).toList()
                );
        Map<UUID, StandardDictionarySummaryResponse> dictionaries = standardDictionaryValueSupport.summaries(
                items.stream()
                        .map(ModelFieldTemplateItem::getStandardDictionaryId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        Map<UUID, List<ModelFieldTemplateFieldResponse>> fieldsByTemplate = new HashMap<>();
        for (ModelFieldTemplateItem item : items) {
            UUID dictionaryId = item.getStandardDictionaryId();
            fieldsByTemplate.computeIfAbsent(item.getTemplateId(), ignored -> new ArrayList<>())
                    .add(ModelFieldTemplateFieldResponse.from(
                            item,
                            dictionaryId == null ? null : dictionaries.get(dictionaryId)
                    ));
        }
        Map<UUID, ModelFieldTemplateResponse> responses = new HashMap<>();
        for (ModelFieldTemplate template : templates) {
            responses.put(template.getId(), ModelFieldTemplateResponse.from(
                    template,
                    fieldsByTemplate.getOrDefault(template.getId(), List.of())
            ));
        }
        return responses;
    }

    private ModelFieldTemplate requireTemplate(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "常用字段模板不存在"));
    }

    private ModelFieldTemplate requireTemplateForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "常用字段模板不存在"));
    }

    private static void requireVersion(ModelFieldTemplate template, int expectedVersion) {
        if (template.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "常用字段模板已被其他用户修改，请刷新后重试");
        }
    }

    private static String normalizeTemplateCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private record PreparedField(
            ModelFieldTemplateFieldInput input,
            String code,
            Integer length,
            Integer precision,
            Integer scale,
            GeometryTypeDefinition geometry
    ) {
    }
}
