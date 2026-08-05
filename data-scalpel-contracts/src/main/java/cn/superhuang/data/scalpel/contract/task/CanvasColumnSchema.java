package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

public record CanvasColumnSchema(
        String name,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        boolean generated,
        String comment,
        GeometryTypeDefinition geometry
) {
    public CanvasColumnSchema(
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            String defaultValue,
            boolean autoIncrement,
            boolean generated,
            String comment
    ) {
        this(
                name, fieldType, length, precision, scale, nullable, defaultValue,
                autoIncrement, generated, comment, null
        );
    }

    public CanvasColumnSchema {
        if (fieldType == PlatformDataType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("GEOMETRY requires a geometry definition");
            }
            if (length != null || precision != null || scale != null) {
                throw new IllegalArgumentException("GEOMETRY does not accept scalar type parameters");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("Only GEOMETRY accepts a geometry definition");
        }
    }
}
