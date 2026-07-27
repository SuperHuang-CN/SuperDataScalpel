package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.util.UUID;

public record FileDatasetTableLoadSubmissionResponse(
        UUID jobId,
        FileDatasetFileResponse file,
        FileDatasetTableResponse table
) {
}
