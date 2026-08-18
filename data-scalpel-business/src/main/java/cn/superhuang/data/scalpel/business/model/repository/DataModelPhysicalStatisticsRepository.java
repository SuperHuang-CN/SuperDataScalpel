package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataModelPhysicalStatisticsRepository extends JpaRepository<DataModelPhysicalStatistics, UUID> {

    Optional<DataModelPhysicalStatistics> findByModelId(UUID modelId);

    List<DataModelPhysicalStatistics> findAllByModelIdIn(Collection<UUID> modelIds);

    void deleteByModelId(UUID modelId);
}
