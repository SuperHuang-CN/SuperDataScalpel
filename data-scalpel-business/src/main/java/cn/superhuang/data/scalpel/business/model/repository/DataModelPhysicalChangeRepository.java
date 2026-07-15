package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataModelPhysicalChangeRepository extends SearchRepository<DataModelPhysicalChange, UUID> {

    Optional<DataModelPhysicalChange> findByIdAndModelId(UUID id, UUID modelId);

    List<DataModelPhysicalChange> findAllByModelIdAndStatusIn(UUID modelId, Collection<DataModelPhysicalChangeStatus> statuses);

    void deleteAllByModelId(UUID modelId);
}
