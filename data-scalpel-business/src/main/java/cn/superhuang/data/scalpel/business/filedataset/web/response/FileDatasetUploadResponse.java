package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.util.List;
import java.util.UUID;

public record FileDatasetUploadResponse(
        List<FileDatasetFileResponse> files,
        List<FileDatasetTableResponse> tables,
        List<UUID> jobIds
) {
    public FileDatasetUploadResponse {
        files = List.copyOf(files);
        tables = List.copyOf(tables);
        jobIds = List.copyOf(jobIds);
    }
}
