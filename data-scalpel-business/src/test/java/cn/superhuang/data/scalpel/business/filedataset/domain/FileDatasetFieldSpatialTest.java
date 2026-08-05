package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileDatasetFieldSpatialTest {

    @Test
    void preservesTheCompleteGeometryDefinition() {
        PlatformTypeDefinition type = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.MULTIPOLYGON,
                CrsReference.epsg(4490),
                CoordinateDimension.XY
        ));

        FileDatasetField field = FileDatasetField.create(
                UUID.randomUUID(), "shape", 0, type, true
        );

        assertEquals(type, field.getTypeDefinition());
    }
}
