package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.util.List;
import java.util.UUID;

public record FileDatasetSchemaResponse(UUID tableId, List<FileDatasetFieldResponse> fields) {
    public FileDatasetSchemaResponse {
        fields = List.copyOf(fields);
    }
}
