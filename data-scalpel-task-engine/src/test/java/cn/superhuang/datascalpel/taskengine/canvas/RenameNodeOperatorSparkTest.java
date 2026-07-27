package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.task.RenameColumnMapping;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RenameNodeOperatorSparkTest {
    private final RenameNodeOperator operator = new RenameNodeOperator();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-rename-operator-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void atomicallyRenamesTableAndColumnsWithoutMutatingUpstreamState() {
        CanvasTableOrigin origin = CanvasTableOrigin.jdbc(UUID.randomUUID(), "public.orders");
        CanvasColumnSchema first = column("a", PlatformDataType.LONG, false, "主键");
        CanvasColumnSchema second = column("b", PlatformDataType.STRING, true, "编码");
        SparkCanvasTable orders = table("orders", origin, List.of(first, second));
        SparkCanvasTable customers = table(
                "customers",
                CanvasTableOrigin.jdbc(UUID.randomUUID(), "public.customers"),
                List.of(column("id", PlatformDataType.LONG, false, "客户主键"))
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("customers", customers);
        inputs.put("orders", orders);

        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                inputs,
                "orders",
                "source_orders",
                List.of(
                        new RenameColumnMapping("a", "b"),
                        new RenameColumnMapping("b", "a")
                ),
                issues
        );

        assertFalse(issues.hasErrors());
        assertEquals(List.of("customers", "source_orders"), List.copyOf(result.propagatedTables().keySet()));
        assertSame(customers, result.propagatedTables().get("customers"));
        SparkCanvasTable renamed = result.propagatedTables().get("source_orders");
        assertNotSame(orders, renamed);
        assertNotSame(orders.dataset(), renamed.dataset());
        assertEquals(origin, renamed.schema().origin());
        assertEquals(List.of("b", "a"), renamed.schema().columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals(PlatformDataType.LONG, renamed.schema().columns().getFirst().fieldType());
        assertEquals("主键", renamed.schema().columns().getFirst().comment());
        assertEquals(PlatformDataType.STRING, renamed.schema().columns().get(1).fieldType());
        assertEquals("编码", renamed.schema().columns().get(1).comment());
        assertEquals(List.of("b", "a"), List.of(renamed.dataset().columns()));

        assertEquals(List.of("customers", "orders"), List.copyOf(inputs.keySet()));
        assertEquals("orders", orders.schema().name());
        assertEquals(List.of("a", "b"), orders.schema().columns().stream().map(CanvasColumnSchema::name).toList());
    }

    @Test
    void rejectsTableColumnAndMappingConflictsBeforeBuildingTheProjection() {
        SparkCanvasTable orders = table(
                "orders",
                null,
                List.of(
                        column("id", PlatformDataType.LONG, false, null),
                        column("order_id", PlatformDataType.LONG, false, null)
                )
        );
        SparkCanvasTable customers = table(
                "customers",
                null,
                List.of(column("id", PlatformDataType.LONG, false, null))
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("orders", orders);
        inputs.put("customers", customers);

        RecordingIssueSink tableIssues = new RecordingIssueSink();
        apply(inputs, "orders", "customers", List.of(), tableIssues);
        assertTrue(tableIssues.codes.contains("DUPLICATE_TABLE_NAME"));

        RecordingIssueSink columnIssues = new RecordingIssueSink();
        apply(
                inputs,
                "orders",
                "orders",
                List.of(new RenameColumnMapping("id", "order_id")),
                columnIssues
        );
        assertTrue(columnIssues.codes.contains("DUPLICATE_COLUMN_NAME"));

        RecordingIssueSink sourceIssues = new RecordingIssueSink();
        apply(
                inputs,
                "orders",
                "orders",
                List.of(
                        new RenameColumnMapping("id", "first_id"),
                        new RenameColumnMapping("id", "second_id")
                ),
                sourceIssues
        );
        assertTrue(sourceIssues.codes.contains("DUPLICATE_RENAME_SOURCE_COLUMN"));
    }

    @Test
    void treatsNoEffectAndRedundantMappingsAsWarnings() {
        Map<String, SparkCanvasTable> inputs = Map.of(
                "orders",
                table("orders", null, List.of(
                        column("id", PlatformDataType.LONG, false, null),
                        column("name", PlatformDataType.STRING, true, null)
                ))
        );

        RecordingIssueSink noEffectIssues = new RecordingIssueSink();
        CanvasNodeOperationResult noEffect = apply(
                inputs,
                "orders",
                "orders",
                List.of(new RenameColumnMapping("id", "id")),
                noEffectIssues
        );
        assertFalse(noEffectIssues.hasErrors());
        assertEquals(List.of("RENAME_HAS_NO_EFFECT"), noEffectIssues.codes);
        assertTrue(noEffect.propagatedTables().containsKey("orders"));

        RecordingIssueSink redundantIssues = new RecordingIssueSink();
        CanvasNodeOperationResult renamed = apply(
                inputs,
                "orders",
                "source_orders",
                List.of(new RenameColumnMapping("id", "id")),
                redundantIssues
        );
        assertFalse(redundantIssues.hasErrors());
        assertEquals(List.of("REDUNDANT_RENAME_MAPPING"), redundantIssues.codes);
        assertTrue(renamed.propagatedTables().containsKey("source_orders"));
    }

    private CanvasNodeOperationResult apply(
            Map<String, SparkCanvasTable> inputs,
            String sourceTableName,
            String outputTableName,
            List<RenameColumnMapping> mappings,
            RecordingIssueSink issues
    ) {
        RenameNodeDefinition node = new RenameNodeDefinition(
                UUID.randomUUID().toString(),
                "重命名",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new RenameConfiguration(sourceTableName, outputTableName, mappings)
        );
        return operator.apply(
                node,
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark)
                )
        );
    }

    private SparkCanvasTable table(
            String name,
            CanvasTableOrigin origin,
            List<CanvasColumnSchema> columns
    ) {
        CanvasTableSchema schema = new CanvasTableSchema(name, origin, columns);
        Dataset<Row> dataset = spark.createDataFrame(
                List.<Row>of(),
                SparkTypeMapper.toStructType(columns)
        );
        return new SparkCanvasTable(schema, dataset);
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable,
            String comment
    ) {
        return new CanvasColumnSchema(
                name,
                type,
                type == PlatformDataType.STRING ? 128 : null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                comment
        );
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }
    }
}
