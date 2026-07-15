package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;

import java.util.List;
import java.util.UUID;

public record FileDatasetParsingResponse(
        UUID id,
        FileDatasetFormat format,
        FileDatasetCompression compression,
        FileDatasetParseStatus parseStatus,
        boolean configured,
        FileDatasetParsingOptionsResponse options,
        String parseError,
        int sampledRecordCount,
        boolean truncated,
        List<FileDatasetFieldResponse> fields
) {
}
