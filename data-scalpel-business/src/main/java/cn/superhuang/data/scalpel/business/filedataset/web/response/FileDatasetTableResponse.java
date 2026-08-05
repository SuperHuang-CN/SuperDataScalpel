package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetParsedMetadata;
import cn.superhuang.data.scalpel.contract.type.CrsReference;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FileDatasetTableResponse(
        UUID id,
        UUID fileDatasetId,
        String code,
        String name,
        FileDatasetParseStatus parseStatus,
        long sourceCount,
        long totalRowCount,
        UUID currentLoadJobId,
        int sampledRecordCount,
        boolean truncated,
        boolean previewSupported,
        Map<String, Object> sourceMetadata,
        CrsReference spatialReferenceOverride,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetTableResponse from(
            FileDatasetTable table,
            FileDatasetParsedMetadata metadata,
            long sourceCount,
            long totalRowCount
    ) {
        return new FileDatasetTableResponse(
                table.getId(), table.getFileDatasetId(), table.getCode(), table.getName(),
                table.getParseStatus(), sourceCount, totalRowCount, table.getCurrentLoadJobId(),
                metadata.sampledRecordCount(), metadata.truncated(),
                table.getParseStatus() != FileDatasetParseStatus.SCHEMA_READY,
                metadata.sourceMetadata(), table.getSpatialReferenceOverride(),
                table.getCreatedAt(), table.getUpdatedAt()
        );
    }
}
