package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryOperationLogRepository;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOperationLogResponse;
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

import java.util.UUID;

@Service
public class DataEntryOperationLogService {

    private final DataEntryOperationLogRepository repository;
    private final SearchEngine searchEngine;

    public DataEntryOperationLogService(DataEntryOperationLogRepository repository, SearchEngine searchEngine) {
        this.repository = repository;
        this.searchEngine = searchEngine;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataEntryOperationLog start(
            DataEntryForm form,
            Integer modelSchemaVersion,
            DataEntryOperationType operationType,
            String username,
            int requestedCount,
            String payload
    ) {
        return repository.saveAndFlush(DataEntryOperationLog.processing(
                form.getId(), form.getModelId(), modelSchemaVersion, operationType, username, requestedCount, payload
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID logId, int affectedCount, String normalizedPayload) {
        DataEntryOperationLog log = require(logId);
        log.succeed(affectedCount, normalizedPayload);
        repository.saveAndFlush(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID logId, String code, String message) {
        DataEntryOperationLog log = require(logId);
        log.fail(code, message);
        repository.saveAndFlush(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void partiallySucceed(
            UUID logId,
            int affectedCount,
            String normalizedPayload,
            String code,
            String message
    ) {
        DataEntryOperationLog log = require(logId);
        log.partiallySucceed(affectedCount, normalizedPayload, code, message);
        repository.saveAndFlush(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataEntryOperationLogResponse> search(UUID formId, SearchRequest request) {
        Specification<DataEntryOperationLog> fixed = (root, query, builder) -> builder.equal(root.get("formId"), formId);
        SearchRequest effective = request == null
                ? new SearchRequest(null, 0, 20, "-createdAt")
                : new SearchRequest(request.search(), request.page(), request.size(),
                        request.sort() == null || request.sort().isBlank() ? "-createdAt" : request.sort());
        Page<DataEntryOperationLog> page = searchEngine.search(effective, DataEntryOperationLog.class, repository, fixed);
        return new PageResponse<>(
                page.getContent().stream().map(DataEntryOperationLogResponse::summary).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataEntryOperationLogResponse get(UUID formId, UUID logId) {
        return DataEntryOperationLogResponse.detail(repository.findByIdAndFormId(logId, formId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "填报操作日志不存在")));
    }

    private DataEntryOperationLog require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("填报操作日志不存在：" + id));
    }
}
