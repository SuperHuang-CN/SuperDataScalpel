package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryRecordChange;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryRecordChangeRepository;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryRecordChangeResponse;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataEntryRecordChangeService {
    private final DataEntryRecordChangeRepository repository;
    private final SearchEngine searchEngine;
    private final ObjectMapper objectMapper;

    public DataEntryRecordChangeService(DataEntryRecordChangeRepository repository, SearchEngine searchEngine,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.searchEngine = searchEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> prepare(DataEntryForm form, UUID operationLogId, DataEntryOperationType type,
            String username, int firstSequence, List<DataModelField> fields, List<Map<String, Object>> submitted,
            List<Map<String, Object>> before) {
        String fieldSnapshot = json(fields.stream().map(field -> Map.of(
                "id", field.getId(), "code", field.getCode(), "name", field.getName(),
                "type", JdbcDataEntryPhysicalMutationPort.type(field))).toList());
        List<DataModelField> keys = fields.stream().filter(DataModelField::isPrimaryKey).toList();
        List<DataEntryRecordChange> changes = new ArrayList<>(submitted.size());
        for (int index = 0; index < submitted.size(); index++) {
            Map<String, Object> values = submitted.get(index);
            Map<String, Object> key = new LinkedHashMap<>();
            keys.forEach(field -> key.put(field.getCode(), values.get(field.getCode())));
            changes.add(DataEntryRecordChange.prepared(operationLogId, form.getId(), recordKey(form.getId(), keys, key),
                    firstSequence + index, type, username, json(key), fieldSnapshot, json(values),
                    before == null ? null : json(before.get(index))));
        }
        return repository.saveAllAndFlush(changes).stream().map(DataEntryRecordChange::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(List<UUID> ids, List<Map<String, Object>> after) {
        List<DataEntryRecordChange> changes = ordered(ids);
        for (int index = 0; index < changes.size(); index++) changes.get(index).succeed(after == null ? null : json(after.get(index)));
        repository.saveAllAndFlush(changes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(List<UUID> ids, int confirmedSuccessCount, boolean unknown, String code, String message,
            List<Map<String, Object>> successfulAfter) {
        List<DataEntryRecordChange> changes = ordered(ids);
        for (int index = 0; index < changes.size(); index++) {
            DataEntryRecordChange change = changes.get(index);
            if (index < confirmedSuccessCount) change.succeed(successfulAfter == null ? null : json(successfulAfter.get(index)));
            else if (unknown) change.unknown(code, message);
            else change.fail(code, message);
        }
        repository.saveAllAndFlush(changes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAfterReadbackFailure(List<UUID> ids, List<Integer> confirmedSuccessIndexes,
            boolean remainingUnknown,
            String code, String message) {
        List<DataEntryRecordChange> changes = ordered(ids);
        java.util.Set<Integer> succeeded = java.util.Set.copyOf(confirmedSuccessIndexes);
        for (int index = 0; index < changes.size(); index++) {
            DataEntryRecordChange change = changes.get(index);
            if (succeeded.contains(index) || remainingUnknown) change.unknown(code, message);
            else change.fail(code, message);
        }
        repository.saveAllAndFlush(changes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeByIndexes(List<UUID> ids, List<Integer> confirmedSuccessIndexes, boolean remainingUnknown,
            String code, String message, List<Map<String, Object>> successfulAfter) {
        List<DataEntryRecordChange> changes = ordered(ids);
        Map<Integer, Map<String, Object>> afterByIndex = new LinkedHashMap<>();
        for (int index = 0; index < confirmedSuccessIndexes.size(); index++) {
            afterByIndex.put(confirmedSuccessIndexes.get(index), successfulAfter.get(index));
        }
        for (int index = 0; index < changes.size(); index++) {
            DataEntryRecordChange change = changes.get(index);
            Map<String, Object> after = afterByIndex.get(index);
            if (after != null) change.succeed(json(after));
            else if (remainingUnknown) change.unknown(code, message);
            else change.fail(code, message);
        }
        repository.saveAllAndFlush(changes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeDeletionByIndexes(List<UUID> ids, List<Integer> confirmedSuccessIndexes,
            boolean remainingUnknown, String code, String message) {
        List<DataEntryRecordChange> changes = ordered(ids);
        java.util.Set<Integer> succeeded = java.util.Set.copyOf(confirmedSuccessIndexes);
        for (int index = 0; index < changes.size(); index++) {
            DataEntryRecordChange change = changes.get(index);
            if (succeeded.contains(index)) change.succeed(null);
            else if (remainingUnknown) change.unknown(code, message);
            else change.fail(code, message);
        }
        repository.saveAllAndFlush(changes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(List<UUID> ids) {
        repository.deleteAllByIdInBatch(ids);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<DataEntryRecordChangeResponse> search(UUID formId, String recordKey, UUID operationLogId,
            SearchRequest request) {
        Specification<DataEntryRecordChange> fixed = (root, query, builder) -> {
            var predicate = builder.equal(root.get("formId"), formId);
            if (recordKey != null && !recordKey.isBlank()) predicate = builder.and(predicate, builder.equal(root.get("recordKey"), recordKey));
            if (operationLogId != null) predicate = builder.and(predicate, builder.equal(root.get("operationLogId"), operationLogId));
            return predicate;
        };
        SearchRequest effective = request == null ? new SearchRequest(null, 0, 20, "-createdAt")
                : new SearchRequest(request.search(), request.page(), request.size(),
                request.sort() == null || request.sort().isBlank() ? "-createdAt" : request.sort());
        Page<DataEntryRecordChange> page = searchEngine.search(effective, DataEntryRecordChange.class, repository, fixed);
        return new PageResponse<>(page.getContent().stream().map(DataEntryRecordChangeResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    @Transactional(readOnly = true)
    public DataEntryRecordChangeResponse get(UUID formId, UUID id) {
        DataEntryRecordChange value = repository.findById(id)
                .filter(change -> change.getFormId().equals(formId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录变更不存在"));
        return DataEntryRecordChangeResponse.from(value);
    }

    public String recordKey(UUID formId, List<DataModelField> fields, Map<String, Object> key) {
        List<DataModelField> ordered = fields.stream().sorted(Comparator.comparing(DataModelField::getId)).toList();
        List<List<String>> identity = ordered.stream().map(field -> List.of(
                field.getId().toString(), DataEntryValueCanonicalizer.canonical(key.get(field.getCode()), field)))
                .toList();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(formId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(json(identity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private List<DataEntryRecordChange> ordered(List<UUID> ids) {
        Map<UUID, DataEntryRecordChange> byId = repository.findAllByIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(DataEntryRecordChange::getId, value -> value));
        return ids.stream().map(id -> {
            DataEntryRecordChange value = byId.get(id);
            if (value == null) throw new IllegalStateException("记录变更不存在：" + id);
            return value;
        }).toList();
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法序列化记录变更", exception); }
    }
}
