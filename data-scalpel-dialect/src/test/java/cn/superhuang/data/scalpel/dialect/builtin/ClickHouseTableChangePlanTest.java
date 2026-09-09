package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialMetadataStrength;
import cn.superhuang.data.scalpel.dialect.model.SpatialStorageEncoding;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableStorageMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseTableChangePlanTest {

    private final ClickHouseDialect dialect = new ClickHouseDialect();

    @Test
    void createsSingleNodeMergeTreeWithExplicitSortingKey() {
        TableDefinition definition = definition("payload", true, List.of("occurred_at"));

        String sql = dialect.planCreateTable(definition).statements().getFirst();

        assertEquals(
                "CREATE TABLE `warehouse`.`events` (`id` Int64, `occurred_at` DateTime64(6, 'UTC'), `payload` Nullable(String)) "
                        + "ENGINE = MergeTree() ORDER BY (`occurred_at`)",
                sql
        );
    }

    @Test
    void plansOneAtomicAlterForRenameAndNullableAddition() {
        TableDefinition before = definition("payload", true, List.of("occurred_at"));
        TableDefinition target = new TableDefinition(
                before.table(),
                List.of(
                        column("id", TableColumnType.LONG, false, columnId("id")),
                        column("occurred_at", TableColumnType.TIMESTAMP, false, columnId("occurred_at")),
                        column("content", TableColumnType.TEXT, true, columnId("payload")),
                        column("source", TableColumnType.TEXT, true, columnId("source"))
                ),
                List.of(),
                TableStorageDefinition.mergeTree(List.of("occurred_at"))
        );

        var plan = dialect.planTableChange(before, target, metadata());

        assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
        assertEquals(TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT, plan.atomicity());
        assertTrue(plan.allowsInPlaceExecution());
        assertEquals(
                "ALTER TABLE `warehouse`.`events` RENAME COLUMN `payload` TO `content`, ADD COLUMN `source` Nullable(String)",
                plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements().getFirst()
        );
    }

    @Test
    void requiresAnEmptyTableForANonNullableAddition() {
        TableDefinition before = definition("payload", true, List.of("occurred_at"));
        TableDefinition target = new TableDefinition(
                before.table(),
                List.of(
                        column("id", TableColumnType.LONG, false, columnId("id")),
                        column("occurred_at", TableColumnType.TIMESTAMP, false, columnId("occurred_at")),
                        column("payload", TableColumnType.TEXT, true, columnId("payload")),
                        column("source", TableColumnType.TEXT, false, columnId("source"))
                ),
                List.of(),
                before.storage()
        );

        var plan = dialect.planTableChange(before, target, metadata());

        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.TABLE_EMPTY));
    }

    @Test
    void rejectsRenamingASortingKeyColumnAndStorageLayoutChanges() {
        TableDefinition before = definition("payload", true, List.of("occurred_at"));
        TableDefinition target = new TableDefinition(
                before.table(),
                List.of(
                        column("id", TableColumnType.LONG, false, columnId("id")),
                        column("event_time", TableColumnType.TIMESTAMP, false, columnId("occurred_at")),
                        column("payload", TableColumnType.TEXT, true, columnId("payload"))
                ),
                List.of(),
                TableStorageDefinition.mergeTree(List.of("event_time"))
        );

        var plan = dialect.planTableChange(before, target, metadata());

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertTrue(plan.reasons().stream().anyMatch(reason -> reason.message().contains("排序键")));
    }

    @Test
    void acceptsAnyValidWkbGeometryMarkerButRejectsAnInvalidMarker() {
        TableIdentifier table = new TableIdentifier("warehouse", null, "spatial_events");
        TableDefinition expected = new TableDefinition(
                table,
                List.of(new TableColumnDefinition(
                        "shape", TableColumnType.GEOMETRY, null, null, null, true, null,
                        new GeometryTypeDefinition(
                                GeometryKind.POINT, CrsReference.epsg(4326), CoordinateDimension.XY
                        )
                )),
                List.of(),
                TableStorageDefinition.mergeTree(List.of())
        );
        TableMetadata differentSemantics = new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(geometryColumn("MULTIPOLYGON", 3857, null)),
                new PrimaryKeyMetadata(null, List.of()),
                List.of(),
                new TableStorageMetadata("MergeTree", List.of())
        );
        TableMetadata invalidMarker = new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(geometryColumn("MULTIPOLYGON", 3857, "空间 marker 损坏")),
                new PrimaryKeyMetadata(null, List.of()),
                List.of(),
                new TableStorageMetadata("MergeTree", List.of())
        );

        assertTrue(dialect.compareTable(expected, differentSemantics).compatible());
        assertFalse(dialect.compareTable(expected, invalidMarker).compatible());
    }

    @Test
    void returnsAnUnsupportedPlanForWkbGeometryNullabilityChanges() {
        TableIdentifier table = new TableIdentifier("warehouse", null, "spatial_events");
        UUID shapeId = columnId("shape");
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                GeometryKind.POINT, CrsReference.epsg(4326), CoordinateDimension.XY
        );
        TableDefinition before = new TableDefinition(
                table,
                List.of(new TableColumnDefinition(
                        "shape", TableColumnType.GEOMETRY, null, null, null, true, shapeId, geometry
                )),
                List.of(),
                TableStorageDefinition.mergeTree(List.of())
        );
        TableDefinition target = new TableDefinition(
                table,
                List.of(new TableColumnDefinition(
                        "shape", TableColumnType.GEOMETRY, null, null, null, false, shapeId, geometry
                )),
                List.of(),
                before.storage()
        );
        TableMetadata actual = new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(geometryColumn("POINT", 4326, null)),
                new PrimaryKeyMetadata(null, List.of()),
                List.of(),
                new TableStorageMetadata("MergeTree", List.of())
        );

        var plan = dialect.planTableChange(before, target, actual);

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertTrue(plan.reasons().stream().anyMatch(reason -> reason.message().contains("可空性调整")));
    }

    private static TableDefinition definition(String payloadName, boolean payloadNullable, List<String> orderByColumns) {
        return new TableDefinition(
                new TableIdentifier("warehouse", null, "events"),
                List.of(
                        column("id", TableColumnType.LONG, false, columnId("id")),
                        column("occurred_at", TableColumnType.TIMESTAMP, false, columnId("occurred_at")),
                        column(payloadName, TableColumnType.TEXT, payloadNullable, columnId("payload"))
                ),
                List.of(),
                TableStorageDefinition.mergeTree(orderByColumns)
        );
    }

    private static TableMetadata metadata() {
        TableIdentifier table = new TableIdentifier("warehouse", null, "events");
        return new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(
                        metadataColumn("id", 1, Types.BIGINT, "Int64", false),
                        metadataColumn("occurred_at", 2, Types.TIMESTAMP, "DateTime", false),
                        metadataColumn("payload", 3, Types.VARCHAR, "Nullable(String)", true)
                ),
                new PrimaryKeyMetadata(null, List.of()),
                List.of(),
                new TableStorageMetadata("MergeTree", List.of("occurred_at"))
        );
    }

    private static ColumnMetadata metadataColumn(String name, int ordinal, int jdbcType, String nativeType, boolean nullable) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, LogicalType.STRING,
                null, null, null, nullable, null, false, false, null
        );
    }

    private static ColumnMetadata geometryColumn(String kind, int epsg, String issue) {
        return new ColumnMetadata(
                "shape", 1, Types.VARCHAR, "String", LogicalType.STRING,
                null, null, null, true, null, false, false, null,
                new SpatialColumnMetadata(
                        kind,
                        null,
                        "EPSG",
                        epsg,
                        CoordinateDimension.XY,
                        true,
                        true,
                        SpatialStorageEncoding.WKB,
                        SpatialMetadataStrength.DECLARED,
                        issue
                )
        );
    }

    private static TableColumnDefinition column(String name, TableColumnType type, boolean nullable, UUID id) {
        return new TableColumnDefinition(name, type, null, null, null, nullable, id);
    }

    private static UUID columnId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
