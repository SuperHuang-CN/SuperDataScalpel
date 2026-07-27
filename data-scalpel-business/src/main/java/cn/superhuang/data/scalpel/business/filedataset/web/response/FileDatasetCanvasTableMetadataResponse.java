package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;

import java.util.List;
import java.util.UUID;

/**
 * Safe Canvas projection of a file dataset table.
 *
 * <p>Storage locations, parsing options, object metadata and credentials are deliberately absent.</p>
 */
public record FileDatasetCanvasTableMetadataResponse(
        UUID fileDatasetTableId,
        UUID fileDatasetId,
        String fileDatasetName,
        FileDatasetType datasetType,
        String code,
        String name,
        FileDatasetParseStatus parseStatus,
        FileDatasetFileStatus fileStatus,
        List<FileDatasetFieldResponse> fields
) {
    public FileDatasetCanvasTableMetadataResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
