package cn.superhuang.data.scalpel.business.standard.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelFieldTemplateItemRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.web.request.CreateStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.CreateStandardDictionaryRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.MoveStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.StandardDictionaryVersionRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.UpdateStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.UpdateStandardDictionaryRequest;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryDetailResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryFieldReferenceResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryItemMutationResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryItemResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryItemTreeResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StandardDictionaryService {

    private static final Comparator<StandardDictionaryItem> ITEM_ORDER = Comparator
            .comparingInt(StandardDictionaryItem::getSortOrder)
            .thenComparing(StandardDictionaryItem::getName)
            .thenComparing(StandardDictionaryItem::getCode)
            .thenComparing(StandardDictionaryItem::getId);

    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository itemRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataModelRepository modelRepository;
    private final ModelFieldTemplateItemRepository templateItemRepository;
    private final StandardDictionaryValueSupport valueSupport;
    private final SearchEngine searchEngine;

    public StandardDictionaryService(
            StandardDictionaryRepository dictionaryRepository,
            StandardDictionaryItemRepository itemRepository,
            DataModelFieldRepository fieldRepository,
            DataModelRepository modelRepository,
            ModelFieldTemplateItemRepository templateItemRepository,
            StandardDictionaryValueSupport valueSupport,
            SearchEngine searchEngine
    ) {
        this.dictionaryRepository = dictionaryRepository;
        this.itemRepository = itemRepository;
        this.fieldRepository = fieldRepository;
        this.modelRepository = modelRepository;
        this.templateItemRepository = templateItemRepository;
        this.valueSupport = valueSupport;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<StandardDictionaryResponse> search(SearchRequest request) {
        SearchRequest effective = request == null
                ? new SearchRequest(null, 0, 20, "-updatedAt,code")
                : new SearchRequest(
                        request.search(),
                        request.page(),
                        request.size(),
                        request.sort() == null || request.sort().isBlank() ? "-updatedAt,code" : request.sort()
                );
        Page<StandardDictionary> page =
                searchEngine.search(effective, StandardDictionary.class, dictionaryRepository);
        return new PageResponse<>(
                page.getContent().stream().map(StandardDictionaryResponse::from).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public StandardDictionaryDetailResponse get(UUID id) {
        StandardDictionary dictionary = requireDictionary(id);
        return detail(dictionary);
    }

    @Transactional
    public StandardDictionaryDetailResponse create(CreateStandardDictionaryRequest request) {
        String code = StandardDictionary.normalizeCode(request.code());
        if (dictionaryRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表编码已存在：" + code);
        }
        StandardDictionary dictionary = StandardDictionary.create(
                code, request.name(), supportedValueType(request.valueType()), true, request.description()
        );
        return detail(dictionaryRepository.saveAndFlush(dictionary));
    }

    @Transactional
    public StandardDictionaryDetailResponse update(UUID id, UpdateStandardDictionaryRequest request) {
        StandardDictionary dictionary = requireVersion(id, request.expectedVersion());
        String code = StandardDictionary.normalizeCode(request.code());
        PlatformDataType valueType = supportedValueType(request.valueType());
        boolean referenced = dictionaryReferenced(id);
        boolean hasItems = itemRepository.countByDictionaryId(id) > 0;
        if (referenced && !dictionary.getCode().equals(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表已被模型字段或常用字段模板引用，不能修改编码");
        }
        if ((referenced || hasItems) && dictionary.getValueType() != valueType) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    referenced ? "码表已被模型字段或常用字段模板引用，不能修改取值类型" : "码表已有节点，不能修改取值类型"
            );
        }
        if (dictionaryRepository.existsByCodeAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表编码已存在：" + code);
        }
        if (dictionary.update(code, request.name(), valueType, request.description())) {
            dictionary.advanceVersion();
            dictionaryRepository.saveAndFlush(dictionary);
        }
        return detail(dictionary);
    }

    @Transactional
    public StandardDictionaryDetailResponse enable(UUID id, StandardDictionaryVersionRequest request) {
        StandardDictionary dictionary = requireVersion(id, request.expectedVersion());
        if (dictionary.enable()) {
            dictionary.advanceVersion();
            dictionaryRepository.saveAndFlush(dictionary);
        }
        return detail(dictionary);
    }

    @Transactional
    public StandardDictionaryDetailResponse disable(UUID id, StandardDictionaryVersionRequest request) {
        StandardDictionary dictionary = requireVersion(id, request.expectedVersion());
        if (dictionary.disable()) {
            dictionary.advanceVersion();
            dictionaryRepository.saveAndFlush(dictionary);
        }
        return detail(dictionary);
    }

    @Transactional
    public void delete(UUID id, StandardDictionaryVersionRequest request) {
        StandardDictionary dictionary = requireVersion(id, request.expectedVersion());
        if (dictionaryReferenced(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表已被模型字段或常用字段模板引用，只能停用，不能删除");
        }
        itemRepository.deleteAllByDictionaryId(id);
        itemRepository.flush();
        dictionaryRepository.delete(dictionary);
        dictionaryRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<StandardDictionaryItemTreeResponse> tree(UUID dictionaryId) {
        StandardDictionary dictionary = requireDictionary(dictionaryId);
        List<StandardDictionaryItem> items =
                itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionaryId);
        Map<UUID, List<StandardDictionaryItem>> children = new HashMap<>();
        for (StandardDictionaryItem item : items) {
            children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item);
        }
        children.values().forEach(list -> list.sort(ITEM_ORDER));
        Set<UUID> visited = new HashSet<>();
        return children.getOrDefault(null, List.of()).stream()
                .map(item -> treeNode(item, dictionary.isEnabled(), children, visited))
                .toList();
    }

    @Transactional
    public StandardDictionaryItemMutationResponse createItem(
            UUID dictionaryId,
            CreateStandardDictionaryItemRequest request
    ) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        validateParent(dictionaryId, request.parentId(), null);
        String code = valueSupport.normalizeItemValue(dictionary.getValueType(), request.code());
        if (itemRepository.existsByDictionaryIdAndCode(dictionaryId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点编码已存在：" + code);
        }
        valueSupport.validateValueAgainstReferences(dictionary, code);
        List<StandardDictionaryItem> siblings = siblings(dictionaryId, request.parentId(), null);
        int index = insertionIndex(request.targetIndex(), siblings.size());
        StandardDictionaryItem item = StandardDictionaryItem.create(
                dictionaryId,
                request.parentId(),
                code,
                request.name(),
                index,
                request.enabled(),
                request.description()
        );
        siblings.add(index, item);
        reindex(siblings);
        itemRepository.saveAllAndFlush(siblings);
        advance(dictionary);
        return mutation(dictionary, item);
    }

    @Transactional
    public StandardDictionaryItemMutationResponse updateItem(
            UUID dictionaryId,
            UUID itemId,
            UpdateStandardDictionaryItemRequest request
    ) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        StandardDictionaryItem item = requireItem(dictionaryId, itemId);
        String code = valueSupport.normalizeItemValue(dictionary.getValueType(), request.code());
        if (!item.getCode().equals(code) && dictionaryReferenced(dictionaryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表已被模型字段或常用字段模板引用，不能修改节点编码");
        }
        if (itemRepository.existsByDictionaryIdAndCodeAndIdNot(dictionaryId, code, itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点编码已存在：" + code);
        }
        valueSupport.validateValueAgainstReferences(dictionary, code);
        if (item.update(code, request.name(), request.description())) {
            itemRepository.saveAndFlush(item);
            advance(dictionary);
        }
        return mutation(dictionary, item);
    }

    @Transactional
    public StandardDictionaryItemMutationResponse moveItem(
            UUID dictionaryId,
            UUID itemId,
            MoveStandardDictionaryItemRequest request
    ) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        StandardDictionaryItem item = requireItem(dictionaryId, itemId);
        validateParent(dictionaryId, request.targetParentId(), itemId);

        UUID oldParentId = item.getParentId();
        List<StandardDictionaryItem> oldSiblings = siblings(dictionaryId, oldParentId, itemId);
        List<StandardDictionaryItem> newSiblings = Objects.equals(oldParentId, request.targetParentId())
                ? oldSiblings
                : siblings(dictionaryId, request.targetParentId(), itemId);
        if (request.targetIndex() > newSiblings.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标同级位置超出范围");
        }
        Set<StandardDictionaryItem> affected = new HashSet<>(oldSiblings);
        affected.addAll(newSiblings);
        affected.add(item);
        Map<UUID, ItemPosition> before = affected.stream().collect(Collectors.toMap(
                StandardDictionaryItem::getId,
                candidate -> new ItemPosition(candidate.getParentId(), candidate.getSortOrder())
        ));
        newSiblings.add(request.targetIndex(), item);
        item.move(request.targetParentId(), request.targetIndex());
        reindex(oldSiblings);
        reindex(newSiblings);
        List<StandardDictionaryItem> changed = affected.stream()
                .filter(candidate -> !before.get(candidate.getId()).matches(candidate))
                .toList();
        if (changed.isEmpty()) {
            return mutation(dictionary, item);
        }
        itemRepository.saveAllAndFlush(changed);
        advance(dictionary);
        return mutation(dictionary, item);
    }

    @Transactional
    public StandardDictionaryItemMutationResponse enableItem(
            UUID dictionaryId,
            UUID itemId,
            StandardDictionaryVersionRequest request
    ) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        StandardDictionaryItem item = requireItem(dictionaryId, itemId);
        if (item.enable()) {
            itemRepository.saveAndFlush(item);
            advance(dictionary);
        }
        return mutation(dictionary, item);
    }

    @Transactional
    public StandardDictionaryItemMutationResponse disableItem(
            UUID dictionaryId,
            UUID itemId,
            StandardDictionaryVersionRequest request
    ) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        StandardDictionaryItem item = requireItem(dictionaryId, itemId);
        if (item.disable()) {
            itemRepository.saveAndFlush(item);
            advance(dictionary);
        }
        return mutation(dictionary, item);
    }

    @Transactional
    public void deleteItem(UUID dictionaryId, UUID itemId, StandardDictionaryVersionRequest request) {
        StandardDictionary dictionary = requireVersion(dictionaryId, request.expectedVersion());
        StandardDictionaryItem item = requireItem(dictionaryId, itemId);
        if (dictionaryReferenced(dictionaryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表已被模型字段或常用字段模板引用，节点只能停用，不能删除");
        }
        if (itemRepository.existsByParentId(itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点包含子节点，不能删除");
        }
        UUID parentId = item.getParentId();
        itemRepository.delete(item);
        itemRepository.flush();
        List<StandardDictionaryItem> siblings = siblings(dictionaryId, parentId, null);
        reindex(siblings);
        itemRepository.saveAllAndFlush(siblings);
        advance(dictionary);
    }

    @Transactional(readOnly = true)
    public PageResponse<StandardDictionaryFieldReferenceResponse> fieldReferences(
            UUID dictionaryId,
            SearchRequest request
    ) {
        requireDictionary(dictionaryId);
        Page<DataModelField> page = searchEngine.search(
                request,
                DataModelField.class,
                fieldRepository,
                (root, query, builder) -> builder.equal(root.get("standardDictionaryId"), dictionaryId)
        );
        Map<UUID, DataModel> models = modelRepository.findAllById(
                page.getContent().stream().map(DataModelField::getModelId).distinct().toList()
        ).stream().collect(Collectors.toMap(DataModel::getId, Function.identity()));
        List<StandardDictionaryFieldReferenceResponse> content = page.getContent().stream()
                .map(field -> fieldReference(field, models.get(field.getModelId())))
                .toList();
        return new PageResponse<>(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    StandardDictionary requireDictionary(UUID id) {
        return dictionaryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "码表不存在"));
    }

    private StandardDictionary requireVersion(UUID id, int expectedVersion) {
        StandardDictionary dictionary = dictionaryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "码表不存在"));
        if (dictionary.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表已被其他人修改，请刷新后重试");
        }
        return dictionary;
    }

    private static PlatformDataType supportedValueType(PlatformDataType valueType) {
        try {
            return StandardDictionary.requireSupportedType(valueType);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private StandardDictionaryItem requireItem(UUID dictionaryId, UUID itemId) {
        StandardDictionaryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "码表节点不存在"));
        if (!item.getDictionaryId().equals(dictionaryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "码表节点不存在");
        }
        return item;
    }

    private void validateParent(UUID dictionaryId, UUID parentId, UUID currentItemId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentItemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点不能以自身为父节点");
        }
        StandardDictionaryItem parent = requireItem(dictionaryId, parentId);
        UUID ancestorId = parent.getParentId();
        Set<UUID> visited = new HashSet<>();
        while (ancestorId != null) {
            if (!visited.add(ancestorId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "码表树存在循环引用");
            }
            if (ancestorId.equals(currentItemId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点不能移动到自己的后代节点下");
            }
            ancestorId = requireItem(dictionaryId, ancestorId).getParentId();
        }
    }

    private List<StandardDictionaryItem> siblings(UUID dictionaryId, UUID parentId, UUID excludedId) {
        return itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionaryId).stream()
                .filter(item -> Objects.equals(item.getParentId(), parentId))
                .filter(item -> !Objects.equals(item.getId(), excludedId))
                .sorted(ITEM_ORDER)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static int insertionIndex(Integer targetIndex, int size) {
        if (targetIndex == null) {
            return size;
        }
        if (targetIndex > size) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标同级位置超出范围");
        }
        return targetIndex;
    }

    private static void reindex(List<StandardDictionaryItem> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            StandardDictionaryItem item = siblings.get(index);
            item.move(item.getParentId(), index);
        }
    }

    private record ItemPosition(UUID parentId, int sortOrder) {

        private boolean matches(StandardDictionaryItem item) {
            return Objects.equals(parentId, item.getParentId()) && sortOrder == item.getSortOrder();
        }
    }

    private void advance(StandardDictionary dictionary) {
        dictionary.advanceVersion();
        dictionaryRepository.saveAndFlush(dictionary);
    }

    private StandardDictionaryDetailResponse detail(StandardDictionary dictionary) {
        return new StandardDictionaryDetailResponse(
                StandardDictionaryResponse.from(dictionary),
                itemRepository.countByDictionaryId(dictionary.getId()),
                fieldRepository.countByStandardDictionaryId(dictionary.getId()),
                templateItemRepository.countByStandardDictionaryId(dictionary.getId())
        );
    }

    private boolean dictionaryReferenced(UUID dictionaryId) {
        return fieldRepository.existsByStandardDictionaryId(dictionaryId)
                || templateItemRepository.existsByStandardDictionaryId(dictionaryId);
    }

    private static StandardDictionaryItemMutationResponse mutation(
            StandardDictionary dictionary,
            StandardDictionaryItem item
    ) {
        return new StandardDictionaryItemMutationResponse(
                dictionary.getVersion(),
                StandardDictionaryItemResponse.from(item)
        );
    }

    private StandardDictionaryItemTreeResponse treeNode(
            StandardDictionaryItem item,
            boolean ancestorsEnabled,
            Map<UUID, List<StandardDictionaryItem>> children,
            Set<UUID> visited
    ) {
        if (!visited.add(item.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表树存在循环引用");
        }
        boolean effectiveEnabled = ancestorsEnabled && item.isEnabled();
        List<StandardDictionaryItemTreeResponse> childNodes = children
                .getOrDefault(item.getId(), List.of()).stream()
                .map(child -> treeNode(child, effectiveEnabled, children, visited))
                .toList();
        return new StandardDictionaryItemTreeResponse(
                item.getId(),
                item.getParentId(),
                item.getCode(),
                item.getName(),
                item.getSortOrder(),
                item.isEnabled(),
                effectiveEnabled,
                item.getDescription(),
                childNodes,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private static StandardDictionaryFieldReferenceResponse fieldReference(
            DataModelField field,
            DataModel model
    ) {
        if (model == null) {
            throw new IllegalStateException("码表字段引用的模型不存在：" + field.getModelId());
        }
        return new StandardDictionaryFieldReferenceResponse(
                model.getId(),
                model.getCode(),
                model.getName(),
                model.getStatus(),
                model.getPhysicalTableMode(),
                model.getSchemaVersion(),
                field.getId(),
                field.getCode(),
                field.getName(),
                field.getFieldType()
        );
    }
}
