package cn.superhuang.data.scalpel.business.dataentry.repository;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryRecordChange;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.List;
import java.util.UUID;

public interface DataEntryRecordChangeRepository extends SearchRepository<DataEntryRecordChange, UUID> {
    List<DataEntryRecordChange> findAllByIdIn(List<UUID> ids);
    void deleteAllByIdInBatch(Iterable<UUID> ids);
    void deleteAllByFormId(UUID formId);
}
