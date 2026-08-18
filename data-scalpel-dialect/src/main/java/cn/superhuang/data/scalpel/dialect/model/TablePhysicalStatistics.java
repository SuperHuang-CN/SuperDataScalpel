package cn.superhuang.data.scalpel.dialect.model;

/** A read-only, point-in-time physical table statistics result. */
public record TablePhysicalStatistics(
        TableStatisticsState state,
        Long rowCount,
        TableStatisticQuality rowCountQuality,
        Long storageBytes,
        TableStatisticQuality storageQuality,
        String message
) {
    public TablePhysicalStatistics {
        state = state == null ? TableStatisticsState.UNSUPPORTED : state;
        rowCountQuality = normalizeQuality(rowCount, rowCountQuality);
        storageQuality = normalizeQuality(storageBytes, storageQuality);
        if (rowCount != null && rowCount < 0) {
            throw new IllegalArgumentException("Row count cannot be negative");
        }
        if (storageBytes != null && storageBytes < 0) {
            throw new IllegalArgumentException("Storage bytes cannot be negative");
        }
        message = message == null || message.isBlank() ? null : message.trim();
    }

    public static TablePhysicalStatistics available(
            Long rowCount,
            TableStatisticQuality rowCountQuality,
            Long storageBytes,
            TableStatisticQuality storageQuality
    ) {
        return new TablePhysicalStatistics(
                TableStatisticsState.AVAILABLE,
                rowCount,
                rowCountQuality,
                storageBytes,
                storageQuality,
                null
        );
    }

    public static TablePhysicalStatistics notFound() {
        return new TablePhysicalStatistics(
                TableStatisticsState.NOT_FOUND,
                null,
                TableStatisticQuality.UNAVAILABLE,
                null,
                TableStatisticQuality.UNAVAILABLE,
                "物理表不存在"
        );
    }

    public static TablePhysicalStatistics unsupported(String message) {
        return new TablePhysicalStatistics(
                TableStatisticsState.UNSUPPORTED,
                null,
                TableStatisticQuality.UNAVAILABLE,
                null,
                TableStatisticQuality.UNAVAILABLE,
                message
        );
    }

    private static TableStatisticQuality normalizeQuality(Long value, TableStatisticQuality quality) {
        if (value == null) {
            return TableStatisticQuality.UNAVAILABLE;
        }
        return quality == null || quality == TableStatisticQuality.UNAVAILABLE
                ? TableStatisticQuality.ESTIMATED
                : quality;
    }
}
