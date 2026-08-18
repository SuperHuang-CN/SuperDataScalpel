package cn.superhuang.data.scalpel.business.dataentry.repository;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataEntryFormRepository extends SearchRepository<DataEntryForm, UUID> {
    boolean existsByModelId(UUID modelId);
    Optional<DataEntryForm> findByModelId(UUID modelId);
    List<DataEntryForm> findAllByModelIdIn(Collection<UUID> modelIds);
}
