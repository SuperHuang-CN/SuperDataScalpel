package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record FileDatasetInputConfiguration(
        String fileDatasetId,
        List<FileDatasetInputTableSelection> tables
) {
    public FileDatasetInputConfiguration {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public FileDatasetInputConfiguration(String fileDatasetTableId) {
        this("", fileDatasetTableId == null
                ? List.of() : List.of(new FileDatasetInputTableSelection(fileDatasetTableId)));
    }

    public String fileDatasetTableId() {
        return tables.isEmpty() ? null : tables.getFirst().fileDatasetTableId();
    }
}
