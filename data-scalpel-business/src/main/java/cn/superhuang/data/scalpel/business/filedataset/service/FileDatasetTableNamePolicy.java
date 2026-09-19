package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableNames;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Enforces dataset-scoped, case-insensitive logical-table name uniqueness. */
@Component
public class FileDatasetTableNamePolicy {

    private final FileDatasetTableRepository tableRepository;

    public FileDatasetTableNamePolicy(FileDatasetTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    public List<String> normalizeForRequest(
            UUID datasetId,
            Collection<String> names,
            Collection<UUID> excludedTableIds
    ) {
        return normalizeAndRequireUnique(datasetId, names, excludedTableIds, false);
    }

    public List<String> normalizeForParsing(
            UUID datasetId,
            Collection<String> names,
            Collection<UUID> excludedTableIds
    ) {
        return normalizeAndRequireUnique(datasetId, names, excludedTableIds, true);
    }

    private List<String> normalizeAndRequireUnique(
            UUID datasetId,
            Collection<String> names,
            Collection<UUID> excludedTableIds,
            boolean parsing
    ) {
        Set<UUID> excluded = excludedTableIds == null ? Set.of() : Set.copyOf(excludedTableIds);
        Set<String> usedKeys = new HashSet<>();
        for (FileDatasetTable table : tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(datasetId)) {
            if (!excluded.contains(table.getId())) {
                usedKeys.add(FileDatasetTableNames.uniquenessKey(table.getName()));
            }
        }
        List<String> normalized;
        try {
            normalized = names.stream().map(FileDatasetTableNames::normalize).toList();
        } catch (IllegalArgumentException exception) {
            if (parsing) {
                throw new FileDatasetParsingException(exception.getMessage(), exception);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        for (String name : normalized) {
            if (!usedKeys.add(FileDatasetTableNames.uniquenessKey(name))) {
                String message = "同一文件数据集内已存在同名数据表：" + name;
                if (parsing) {
                    throw new FileDatasetParsingException(message);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, message);
            }
        }
        return normalized;
    }
}
