package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CanvasTableSchema(
        String name,
        CanvasTableOrigin origin,
        List<CanvasColumnSchema> columns,
        CanvasDatasetKind datasetKind,
        String eventTimeColumn,
        String watermarkDelay
) {
    public CanvasTableSchema {
        columns = columns == null ? List.of() : List.copyOf(columns);
        datasetKind = datasetKind == null ? CanvasDatasetKind.BOUNDED : datasetKind;
    }

    public CanvasTableSchema(String name, CanvasTableOrigin origin, List<CanvasColumnSchema> columns) {
        this(name, origin, columns, CanvasDatasetKind.BOUNDED, null, null);
    }
}
