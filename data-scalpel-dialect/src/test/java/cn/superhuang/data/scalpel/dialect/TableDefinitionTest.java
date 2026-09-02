package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableDefinitionTest {

    @Test
    void fingerprintIgnoresTableLocationColumnIdentityAndPhysicalColumnOrder() {
        TableDefinition first = new TableDefinition(
                new TableIdentifier("warehouse", "public", "orders"),
                List.of(
                        new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false, UUID.randomUUID()),
                        new TableColumnDefinition("title", TableColumnType.STRING, 120, null, null, true, UUID.randomUUID())
                ),
                List.of("order_id")
        );
        TableDefinition second = new TableDefinition(
                new TableIdentifier("another_database", "archive", "orders_backup"),
                List.of(
                        new TableColumnDefinition("title", TableColumnType.STRING, 120, null, null, true, UUID.randomUUID()),
                        new TableColumnDefinition("ORDER_ID", TableColumnType.LONG, null, null, null, false, UUID.randomUUID())
                ),
                List.of("ORDER_ID")
        );

        assertEquals(first.structureFingerprint(), second.structureFingerprint());
    }

    @Test
    void fingerprintChangesWhenAnEffectiveStructuralAttributeChanges() {
        TableDefinition base = definition(TableColumnType.STRING, 120, true, List.of("order_id"));

        assertNotEquals(base.structureFingerprint(), definition(TableColumnType.STRING, 100, true, List.of("order_id")).structureFingerprint());
        assertNotEquals(base.structureFingerprint(), definition(TableColumnType.TEXT, null, true, List.of("order_id")).structureFingerprint());
        assertNotEquals(base.structureFingerprint(), definition(TableColumnType.STRING, 120, false, List.of("order_id")).structureFingerprint());
        assertNotEquals(base.structureFingerprint(), definition(TableColumnType.STRING, 120, true, List.of("title", "order_id")).structureFingerprint());
    }

    @Test
    void fingerprintChangesWhenManagedStorageConfigurationChanges() {
        TableDefinition first = new TableDefinition(
                new TableIdentifier("warehouse", null, "orders"),
                List.of(new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false)),
                List.of(),
                TableStorageDefinition.mergeTree(List.of("order_id"))
        );
        TableDefinition second = new TableDefinition(
                new TableIdentifier("warehouse", null, "orders"),
                List.of(new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false)),
                List.of(),
                TableStorageDefinition.mergeTree(List.of())
        );

        assertNotEquals(first.structureFingerprint(), second.structureFingerprint());
    }

    @Test
    void geometrySnapshotMayOmitSemanticDefinitionButScalarsCannotCarryOne() {
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                GeometryKind.POINT, CrsReference.epsg(4326), CoordinateDimension.XY
        );

        TableColumnDefinition snapshot = new TableColumnDefinition(
                "shape", TableColumnType.GEOMETRY, null, null, null, true, null, null
        );
        assertEquals(TableColumnType.GEOMETRY, snapshot.type());
        assertEquals(null, snapshot.geometry());
        assertThrows(
                IllegalArgumentException.class,
                () -> new TableColumnDefinition(
                        "name", TableColumnType.STRING, 100, null, null, true, null, geometry
                )
        );
    }

    private static TableDefinition definition(
            TableColumnType titleType,
            Integer titleLength,
            boolean titleNullable,
            List<String> primaryKeyColumns
    ) {
        return new TableDefinition(
                new TableIdentifier(null, "public", "orders"),
                List.of(
                        new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false),
                        new TableColumnDefinition("title", titleType, titleLength, null, null, titleNullable)
                ),
                primaryKeyColumns
        );
    }
}
