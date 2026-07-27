package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;

/**
 * Converts parser-internal inference types into the stable platform type system.
 *
 * <p>{@link LogicalType} remains an implementation detail of the sampling helpers. Persisted file
 * schemas and every contract leaving the file parsing boundary use {@link PlatformTypeDefinition}.</p>
 */
final class FileDatasetTypeDefinitions {

    private static final int DEFAULT_DECIMAL_PRECISION = 38;
    private static final int DEFAULT_DECIMAL_SCALE = 18;

    private FileDatasetTypeDefinitions() {
    }

    static PlatformTypeDefinition fromLogicalType(LogicalType logicalType) {
        if (logicalType == null) {
            return PlatformTypeDefinition.string(null);
        }
        return switch (logicalType) {
            case BOOLEAN -> PlatformTypeDefinition.of(PlatformDataType.BOOLEAN);
            case INTEGER -> PlatformTypeDefinition.of(PlatformDataType.LONG);
            case DECIMAL -> decimal(DEFAULT_DECIMAL_PRECISION, DEFAULT_DECIMAL_SCALE);
            case STRING -> PlatformTypeDefinition.string(null);
            case BINARY -> PlatformTypeDefinition.of(PlatformDataType.BINARY);
            case DATE -> PlatformTypeDefinition.of(PlatformDataType.DATE);
            case DATETIME -> PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ);
            case TIME, JSON, ARRAY, OTHER -> PlatformTypeDefinition.string(null);
        };
    }

    static PlatformTypeDefinition decimal(int precision, int scale) {
        int normalizedPrecision = Math.max(1, Math.min(38, precision));
        int normalizedScale = Math.max(0, Math.min(normalizedPrecision, scale));
        return PlatformTypeDefinition.decimal(normalizedPrecision, normalizedScale);
    }
}
