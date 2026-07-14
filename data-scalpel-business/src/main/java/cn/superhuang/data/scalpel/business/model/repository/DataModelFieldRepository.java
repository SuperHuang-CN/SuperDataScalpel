package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataModelFieldRepository extends JpaRepository<DataModelField, UUID> {

    List<DataModelField> findAllByModelIdOrderBySortOrderAscCodeAsc(UUID modelId);

    void deleteAllByModelId(UUID modelId);
}
