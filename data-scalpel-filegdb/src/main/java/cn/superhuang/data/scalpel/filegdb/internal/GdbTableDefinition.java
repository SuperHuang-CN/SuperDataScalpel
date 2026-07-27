package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import java.util.List;

record GdbTableDefinition(
        String physicalName,
        String tableFileName,
        int declaredRecordCount,
        int largestRecordBytes,
        int geometryTypeCode,
        int geometryProperties,
        FileGdbLayerType layerType,
        List<GdbFieldDefinition> fields,
        FileGdbSpatialReference spatialReference) {

    GdbTableDefinition {
        fields = List.copyOf(fields);
    }

    int nullableFieldCount() {
        return (int) fields.stream().filter(field -> field.publicField().nullable()).count();
    }
}
