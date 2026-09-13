package cn.superhuang.data.scalpel.contract.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** One platform logical type together with the parameters that affect its physical representation. */
@JsonClassDescription("一个平台逻辑数据类型及影响物理表示的类型参数；只有 STRING、DECIMAL 和 GEOMETRY 使用附加参数。")
public record PlatformTypeDefinition(
        @JsonPropertyDescription("平台逻辑数据类型；STRING 使用 length，DECIMAL 使用 precision/scale，GEOMETRY 使用 geometry。")
        PlatformDataType type,
        @JsonPropertyDescription("STRING 类型允许的最大字符数；未限制时可为空，其他类型必须为空。")
        Integer length,
        @JsonPropertyDescription("DECIMAL 类型的总有效位数，范围 1 到 38；其他类型必须为空。")
        Integer precision,
        @JsonPropertyDescription("DECIMAL 类型的小数位数，范围 0 到 precision；其他类型必须为空。")
        Integer scale,
        @JsonPropertyDescription("GEOMETRY 类型必填的几何子类型、坐标系和维度定义；其他类型必须为空。")
        GeometryTypeDefinition geometry
) {

    /** Backward-compatible constructor for existing scalar call sites and serialized contracts. */
    public PlatformTypeDefinition(
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        this(type, length, precision, scale, null);
    }

    public PlatformTypeDefinition {
        if (type == null) {
            throw new IllegalArgumentException("Platform data type is required");
        }
        if (type == PlatformDataType.STRING) {
            if (length != null && length < 1) {
                throw new IllegalArgumentException("String length must be positive when specified");
            }
        } else if (length != null) {
            throw new IllegalArgumentException("Only STRING accepts a length");
        }
        if (type == PlatformDataType.DECIMAL) {
            if (precision == null || precision < 1 || precision > 38) {
                throw new IllegalArgumentException("Decimal precision must be between 1 and 38");
            }
            if (scale == null || scale < 0 || scale > precision) {
                throw new IllegalArgumentException("Decimal scale must be between 0 and precision");
            }
        } else if (precision != null || scale != null) {
            throw new IllegalArgumentException("Only DECIMAL accepts precision and scale");
        }
        if (type == PlatformDataType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("GEOMETRY requires a geometry definition");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("Only GEOMETRY accepts a geometry definition");
        }
    }

    public static PlatformTypeDefinition of(PlatformDataType type) {
        return new PlatformTypeDefinition(type, null, null, null);
    }

    public static PlatformTypeDefinition string(Integer length) {
        return new PlatformTypeDefinition(PlatformDataType.STRING, length, null, null);
    }

    public static PlatformTypeDefinition decimal(int precision, int scale) {
        return new PlatformTypeDefinition(PlatformDataType.DECIMAL, null, precision, scale);
    }

    public static PlatformTypeDefinition geometry(GeometryTypeDefinition geometry) {
        return new PlatformTypeDefinition(PlatformDataType.GEOMETRY, null, null, null, geometry);
    }
}
