package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInput;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerInputRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerSummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ModelWarehouseLayerService {

    private static final Pattern MODEL_CODE_PREFIX = Pattern.compile("[a-z][a-z0-9_]{0,30}_");
    private static final Comparator<ModelWarehouseLayer> LAYER_ORDER = Comparator
            .comparingInt(ModelWarehouseLayer::getSortOrder)
            .thenComparing(ModelWarehouseLayer::getCode)
            .thenComparing(ModelWarehouseLayer::getId);

    private final ModelWarehouseLayerRepository repository;
    private final ModelWarehouseLayerInputRepository inputRepository;
    private final DataModelRepository modelRepository;
    private final SearchEngine searchEngine;

    public ModelWarehouseLayerService(
            ModelWarehouseLayerRepository repository,
            ModelWarehouseLayerInputRepository inputRepository,
            DataModelRepository modelRepository,
            SearchEngine searchEngine
    ) {
        this.repository = repository;
        this.inputRepository = inputRepository;
        this.modelRepository = modelRepository;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModelWarehouseLayerResponse> search(SearchRequest request) {
        Page<ModelWarehouseLayer> page = searchEngine.search(
                request,
                ModelWarehouseLayer.class,
                repository
        );
        Map<UUID, ModelWarehouseLayerResponse> responses = responses(page.getContent());
        return new PageResponse<>(
                page.getContent().stream().map(layer -> responses.get(layer.getId())).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional
    public ModelWarehouseLayerResponse create(CreateModelWarehouseLayerRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层编码已存在");
        }
        RuleConfiguration rules = normalizeRules(
                request.modelCodePrefix(),
                request.inputLayerPolicy(),
                request.allowedInputLayerIds()
        );
        ModelWarehouseLayer layer = ModelWarehouseLayer.create(
                code,
                request.name(),
                request.description(),
                request.color(),
                request.sortOrder(),
                rules.modelCodePrefix(),
                rules.policy()
        );
        repository.saveAndFlush(layer);
        replaceAllowedInputs(layer.getId(), rules.allowedInputLayerIds(), Set.of());
        return response(layer);
    }

    @Transactional
    public ModelWarehouseLayerResponse update(UUID id, UpdateModelWarehouseLayerRequest request) {
        ModelWarehouseLayer layer = requireLayer(id);
        String code = normalizeCode(request.code());
        long modelCount = modelRepository.countByWarehouseLayerId(id);
        if (!layer.getCode().equals(code) && modelCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层已被模型引用，不能修改编码");
        }
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层编码已存在");
        }
        RuleConfiguration rules = normalizeRules(
                request.modelCodePrefix(),
                request.inputLayerPolicy(),
                request.allowedInputLayerIds()
        );
        Set<UUID> existingInputIds = inputRepository.findAllByTargetLayerId(id).stream()
                .map(ModelWarehouseLayerInput::getInputLayerId)
                .collect(Collectors.toSet());
        validateAllowedInputLayers(rules.allowedInputLayerIds(), existingInputIds);
        layer.update(code, request.name(), request.description(), request.color(), request.sortOrder());
        layer.configureModelingRules(rules.modelCodePrefix(), rules.policy());
        repository.saveAndFlush(layer);
        replaceAllowedInputsWithoutValidation(id, rules.allowedInputLayerIds());
        return response(layer);
    }

    @Transactional
    public ModelWarehouseLayerResponse enable(UUID id) {
        ModelWarehouseLayer layer = requireLayer(id);
        layer.enable();
        repository.saveAndFlush(layer);
        return response(layer);
    }

    @Transactional
    public ModelWarehouseLayerResponse disable(UUID id) {
        ModelWarehouseLayer layer = requireLayer(id);
        layer.disable();
        repository.saveAndFlush(layer);
        return response(layer);
    }

    @Transactional
    public void delete(UUID id) {
        ModelWarehouseLayer layer = requireLayer(id);
        if (modelRepository.existsByWarehouseLayerId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层已被模型引用，不能删除");
        }
        if (inputRepository.existsByInputLayerIdAndTargetLayerIdNot(id, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层已被其他分层配置为允许输入，不能删除");
        }
        inputRepository.deleteAllByTargetLayerId(id);
        inputRepository.flush();
        repository.delete(layer);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public ModelWarehouseLayer requireEnabled(UUID id) {
        ModelWarehouseLayer layer = requireLayer(id);
        if (!layer.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层已停用");
        }
        return layer;
    }

    private void replaceAllowedInputs(
            UUID targetLayerId,
            List<UUID> inputLayerIds,
            Set<UUID> retainedDisabledLayerIds
    ) {
        validateAllowedInputLayers(inputLayerIds, retainedDisabledLayerIds);
        replaceAllowedInputsWithoutValidation(targetLayerId, inputLayerIds);
    }

    private void replaceAllowedInputsWithoutValidation(UUID targetLayerId, List<UUID> inputLayerIds) {
        inputRepository.deleteAllByTargetLayerId(targetLayerId);
        inputRepository.flush();
        if (!inputLayerIds.isEmpty()) {
            inputRepository.saveAllAndFlush(inputLayerIds.stream()
                    .map(inputLayerId -> ModelWarehouseLayerInput.create(targetLayerId, inputLayerId))
                    .toList());
        }
    }

    private void validateAllowedInputLayers(
            List<UUID> inputLayerIds,
            Set<UUID> retainedDisabledLayerIds
    ) {
        if (inputLayerIds.isEmpty()) {
            return;
        }
        Map<UUID, ModelWarehouseLayer> layersById = repository.findAllById(inputLayerIds).stream()
                .collect(Collectors.toMap(ModelWarehouseLayer::getId, Function.identity()));
        if (layersById.size() != inputLayerIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "允许输入分层包含不存在的分层");
        }
        for (UUID inputLayerId : inputLayerIds) {
            ModelWarehouseLayer inputLayer = layersById.get(inputLayerId);
            if (!inputLayer.isEnabled() && !retainedDisabledLayerIds.contains(inputLayerId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "不能新增已停用的允许输入分层：" + inputLayer.getName()
                );
            }
        }
    }

    private RuleConfiguration normalizeRules(
            String modelCodePrefix,
            ModelWarehouseLayerInputPolicy inputLayerPolicy,
            List<UUID> allowedInputLayerIds
    ) {
        String prefix = normalizeOptional(modelCodePrefix);
        if (prefix != null && !MODEL_CODE_PREFIX.matcher(prefix).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "模型编码前缀必须以小写字母开头、以下划线结尾，且最多 32 个字符"
            );
        }
        ModelWarehouseLayerInputPolicy policy = inputLayerPolicy == null
                ? ModelWarehouseLayerInputPolicy.UNRESTRICTED
                : inputLayerPolicy;
        List<UUID> requestedIds = allowedInputLayerIds == null ? List.of() : List.copyOf(allowedInputLayerIds);
        if (requestedIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "允许输入分层不能为空");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "允许输入分层不能重复");
        }
        if (policy == ModelWarehouseLayerInputPolicy.UNRESTRICTED && !requestedIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未限制输入时不能配置允许输入分层");
        }
        return new RuleConfiguration(prefix, policy, new ArrayList<>(uniqueIds));
    }

    private ModelWarehouseLayerResponse response(ModelWarehouseLayer layer) {
        return responses(List.of(layer)).get(layer.getId());
    }

    private Map<UUID, ModelWarehouseLayerResponse> responses(List<ModelWarehouseLayer> layers) {
        if (layers.isEmpty()) {
            return Map.of();
        }
        List<UUID> layerIds = layers.stream().map(ModelWarehouseLayer::getId).toList();
        Map<UUID, Long> modelCounts = modelRepository.countByWarehouseLayerIdIn(layerIds).stream()
                .collect(Collectors.toMap(
                        DataModelRepository.WarehouseLayerModelCount::warehouseLayerId,
                        DataModelRepository.WarehouseLayerModelCount::modelCount
                ));
        Map<UUID, Long> inputReferenceCounts = inputRepository
                .countOtherTargetLayersByInputLayerIdIn(layerIds).stream()
                .collect(Collectors.toMap(
                        ModelWarehouseLayerInputRepository.InputReferenceCount::inputLayerId,
                        ModelWarehouseLayerInputRepository.InputReferenceCount::layerCount
                ));
        List<ModelWarehouseLayerInput> relations = inputRepository.findAllByTargetLayerIdIn(layerIds);
        Set<UUID> inputLayerIds = relations.stream()
                .map(ModelWarehouseLayerInput::getInputLayerId)
                .collect(Collectors.toSet());
        Map<UUID, ModelWarehouseLayer> inputLayersById = inputLayerIds.isEmpty()
                ? Map.of()
                : repository.findAllById(inputLayerIds).stream()
                        .collect(Collectors.toMap(ModelWarehouseLayer::getId, Function.identity()));
        Map<UUID, List<ModelWarehouseLayer>> allowedLayersByTarget = new HashMap<>();
        for (ModelWarehouseLayerInput relation : relations) {
            ModelWarehouseLayer inputLayer = inputLayersById.get(relation.getInputLayerId());
            if (inputLayer != null) {
                allowedLayersByTarget.computeIfAbsent(relation.getTargetLayerId(), ignored -> new ArrayList<>())
                        .add(inputLayer);
            }
        }
        Map<UUID, ModelWarehouseLayerResponse> responses = new HashMap<>();
        for (ModelWarehouseLayer layer : layers) {
            List<ModelWarehouseLayerSummaryResponse> allowedInputs = allowedLayersByTarget
                    .getOrDefault(layer.getId(), List.of()).stream()
                    .sorted(LAYER_ORDER)
                    .map(ModelWarehouseLayerSummaryResponse::from)
                    .toList();
            responses.put(layer.getId(), ModelWarehouseLayerResponse.from(
                    layer,
                    allowedInputs,
                    modelCounts.getOrDefault(layer.getId(), 0L),
                    inputReferenceCounts.getOrDefault(layer.getId(), 0L)
            ));
        }
        return responses;
    }

    private ModelWarehouseLayer requireLayer(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数仓分层不存在"));
    }

    private static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record RuleConfiguration(
            String modelCodePrefix,
            ModelWarehouseLayerInputPolicy policy,
            List<UUID> allowedInputLayerIds
    ) {
    }
}
