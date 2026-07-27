package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;

public record ShapefileHeader(long fileLength, ShapefileShapeType shapeType, ShapefileEnvelope envelope) {
}
