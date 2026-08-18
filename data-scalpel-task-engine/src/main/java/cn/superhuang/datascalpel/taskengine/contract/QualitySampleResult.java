package cn.superhuang.datascalpel.taskengine.contract;

import java.util.List;

public record QualitySampleResult(
        QualitySampleStatus status,
        Long sampledRows,
        Long violationRows,
        Boolean truncated,
        Long sizeBytes,
        String sha256,
        Boolean rowLocatable,
        List<QualitySampleColumn> columns
) {
    public QualitySampleResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        if (status == null) throw new IllegalArgumentException("质检样本状态不能为空");
        boolean available = status == QualitySampleStatus.AVAILABLE;
        if (available && (sampledRows == null || sampledRows < 1 || sampledRows > 1000
                || violationRows == null || violationRows < sampledRows || truncated == null
                || truncated != (sampledRows < violationRows) || sizeBytes == null || sizeBytes < 8
                || sizeBytes > 20L * 1024 * 1024 || sha256 == null
                || !sha256.matches("[0-9a-f]{64}") || rowLocatable == null || columns.isEmpty())
                || !available && (sampledRows != null || violationRows != null || truncated != null
                || sizeBytes != null || sha256 != null || rowLocatable != null || !columns.isEmpty())) {
            throw new IllegalArgumentException("质检样本结果无效");
        }
    }

    public static QualitySampleResult state(QualitySampleStatus status) {
        return new QualitySampleResult(status, null, null, null, null, null, null, List.of());
    }
}
