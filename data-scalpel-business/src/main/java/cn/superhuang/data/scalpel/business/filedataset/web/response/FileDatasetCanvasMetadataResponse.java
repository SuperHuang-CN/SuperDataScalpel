package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.util.List;

public record FileDatasetCanvasMetadataResponse(
        List<FileDatasetCanvasTableMetadataResponse> tables
) {
    public FileDatasetCanvasMetadataResponse {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
