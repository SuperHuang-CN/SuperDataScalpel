package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

final class OutputTypeConversionPolicy {

    private OutputTypeConversionPolicy() {
    }

    static ConversionRisk assignment(CanvasColumnSchema source, CanvasColumnSchema target) {
        if (source.fieldType() == target.fieldType()) {
            if (source.fieldType() == PlatformDataType.STRING
                    && target.length() != null
                    && (source.length() == null || source.length() > target.length())) {
                return ConversionRisk.RISKY;
            }
            if (source.fieldType() == PlatformDataType.DECIMAL && !decimalFits(source, target)) {
                return ConversionRisk.RISKY;
            }
            return ConversionRisk.EXACT;
        }
        if (integral(source.fieldType()) && integral(target.fieldType())) {
            return integralRank(source.fieldType()) <= integralRank(target.fieldType())
                    ? ConversionRisk.SAFE : ConversionRisk.RISKY;
        }
        if (source.fieldType() == PlatformDataType.FLOAT
                && target.fieldType() == PlatformDataType.DOUBLE) {
            return ConversionRisk.SAFE;
        }
        if (integral(source.fieldType()) && target.fieldType() == PlatformDataType.DECIMAL) {
            int targetIntegerDigits = target.precision() - target.scale();
            return targetIntegerDigits >= integralDecimalDigits(source.fieldType())
                    ? ConversionRisk.SAFE : ConversionRisk.RISKY;
        }
        return ConversionRisk.RISKY;
    }

    static boolean needsSparkCast(CanvasColumnSchema source, CanvasColumnSchema target) {
        if (source.fieldType() != target.fieldType()) {
            return true;
        }
        return source.fieldType() == PlatformDataType.DECIMAL
                && (!source.precision().equals(target.precision())
                || !source.scale().equals(target.scale()));
    }

    private static boolean decimalFits(CanvasColumnSchema source, CanvasColumnSchema target) {
        int sourceIntegerDigits = source.precision() - source.scale();
        int targetIntegerDigits = target.precision() - target.scale();
        return source.scale() <= target.scale() && sourceIntegerDigits <= targetIntegerDigits;
    }

    private static boolean integral(PlatformDataType type) {
        return type == PlatformDataType.BYTE
                || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER
                || type == PlatformDataType.LONG;
    }

    private static int integralRank(PlatformDataType type) {
        return switch (type) {
            case BYTE -> 1;
            case SHORT -> 2;
            case INTEGER -> 3;
            case LONG -> 4;
            default -> throw new IllegalArgumentException("Not an integral type: " + type);
        };
    }

    private static int integralDecimalDigits(PlatformDataType type) {
        return switch (type) {
            case BYTE -> 3;
            case SHORT -> 5;
            case INTEGER -> 10;
            case LONG -> 19;
            default -> throw new IllegalArgumentException("Not an integral type: " + type);
        };
    }

    enum ConversionRisk {
        EXACT,
        SAFE,
        RISKY
    }
}
