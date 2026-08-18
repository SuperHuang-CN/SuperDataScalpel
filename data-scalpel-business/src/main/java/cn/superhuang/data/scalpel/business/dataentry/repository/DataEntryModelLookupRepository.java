package cn.superhuang.data.scalpel.business.dataentry.repository;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataEntryModelLookupRepository extends JpaRepository<DataEntryModelLookup, UUID> {
    List<DataEntryModelLookup> findAllByFormIdOrderByTargetFieldId(UUID formId);
    Optional<DataEntryModelLookup> findByFormIdAndTargetFieldId(UUID formId, UUID targetFieldId);
    void deleteAllByFormId(UUID formId);
}
