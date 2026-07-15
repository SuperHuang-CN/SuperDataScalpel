package cn.superhuang.data.scalpel.business.filedataset.repository;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileDatasetFieldRepository extends JpaRepository<FileDatasetField, UUID> {

    List<FileDatasetField> findByFileDatasetIdOrderBySortOrderAsc(UUID fileDatasetId);

    void deleteByFileDatasetId(UUID fileDatasetId);
}
