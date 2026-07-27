package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface FileDatasetFieldRepository extends JpaRepository<FileDatasetField, UUID> {

    List<FileDatasetField> findByFileDatasetTableIdOrderBySortOrderAsc(UUID fileDatasetTableId);

    List<FileDatasetField> findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSortOrderAsc(
            Collection<UUID> fileDatasetTableIds
    );

    void deleteByFileDatasetTableId(UUID fileDatasetTableId);

    void deleteByFileDatasetTableIdIn(List<UUID> fileDatasetTableIds);
}
