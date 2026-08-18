package cn.superhuang.data.scalpel.business.dataentry.repository;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface DataEntryOperationLogRepository extends SearchRepository<DataEntryOperationLog, UUID> {
    Optional<DataEntryOperationLog> findByIdAndFormId(UUID id, UUID formId);
    void deleteAllByFormId(UUID formId);
}
