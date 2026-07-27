package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;

public record DbfFieldDefinition(ShapefileField field, char physicalType, int offset) {
}
