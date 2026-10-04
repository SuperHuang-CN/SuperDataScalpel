package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.*;
import cn.superhuang.datascalpel.taskengine.contract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses existing authorized databases, never creates an application or a container. */
@EnabledIfEnvironmentVariable(named="DATASCALPEL_WRITE_IT_DATABASES", matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchJdbcWriterIntegrationTest {
    private SparkSession spark;
    @BeforeAll void start() {
        spark=cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport.builder().master("local[2,2]").appName("batch-write-real-jdbc-it")
                .config("spark.ui.enabled","false").config("spark.driver.host","127.0.0.1")
                .config("spark.driver.bindAddress","127.0.0.1").config("spark.sql.caseSensitive","true")
                .config("spark.sql.shuffle.partitions","2").getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport.initialize(spark);
    }
    @AfterAll void stop() { if(spark!=null)spark.stop(); }
    @TestFactory List<DynamicTest> databaseCases() throws Exception {
        var values=new ObjectMapper().readTree(System.getenv("DATASCALPEL_WRITE_IT_DATABASES"));
        List<DynamicTest> tests=new ArrayList<>();
        for(var db:values) {
            RuntimeDataSource source=new RuntimeDataSource(UUID.randomUUID(),RuntimeDatabaseType.valueOf(db.path("type").asText()),
                    Set.of(DataSourcePurpose.DISTRIBUTION), new RuntimeJdbcConnection(db.path("driver").asText(),db.path("url").asText(),
                    db.path("catalog").asText(null),db.path("schema").asText(null),db.path("user").asText(),db.path("password").asText(),Map.of()));
            tests.add(DynamicTest.dynamicTest(source.databaseType().name(),()->exercise(source)));
        }
        return tests;
    }
    private Dataset<Row> rows(Row... rows) {
        return spark.createDataFrame(List.of(rows),new StructType().add("id",DataTypes.LongType,true)
                .add("region",DataTypes.IntegerType,true).add("payload",DataTypes.StringType,true)).repartition(2);
    }
    private void exercise(RuntimeDataSource source) throws Exception {
        String name="dswr_"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
        var dialect=BuiltInDialects.registry().require(source.databaseType().name());
        var table=new TableIdentifier(source.connection().catalogName(),source.connection().schemaName(),name);
        String target=dialect.qualifiedName(table);
        String id=dialect.quoteIdentifier("id"),region=dialect.quoteIdentifier("region"),payload=dialect.quoteIdentifier("payload");
        String longType=source.databaseType()==RuntimeDatabaseType.ORACLE?"NUMBER(19)":"BIGINT";
        try(var connection=DriverManager.getConnection(source.connection().jdbcUrl(),SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()));
            var sql=connection.createStatement()) {
            sql.executeUpdate("CREATE TABLE "+target+" ("+id+" "+longType+" PRIMARY KEY, "+region+" INTEGER, "+payload+" VARCHAR(40))");
            try {
                // Legacy mode keeps its old semantics but now uses one shared physical writer.
                DirectJdbcWriter.write(source, table, target,
                        rows(RowFactory.create(1L, 1, "old"), RowFactory.create(2L, 2, "keep")),
                        "APPEND", List.of(), Map.of());
                assertCount(sql, target, 2);
                DirectJdbcWriter.write(source, table, target,
                        rows(RowFactory.create(1L, 1, "updated"), RowFactory.create(3L, null, null)),
                        "UPSERT", List.of("id"), Map.of());
                assertCount(sql, target, 3);
                assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM " + target + " WHERE " + id
                        + "=3 AND " + region + " IS NULL AND " + payload + " IS NULL"));
                assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM " + target + " WHERE " + payload + "='updated'"));
                DirectJdbcWriter.write(source, table, target, rows(RowFactory.create(4L, 4, "replaced")),
                        "OVERWRITE", List.of(), Map.of());
                assertCount(sql, target, 1);
                // No batchWrite means no empty-input guard: retain the existing clear-table behavior.
                DirectJdbcWriter.write(source, table, target, rows(), "OVERWRITE", List.of(), Map.of());
                assertCount(sql, target, 0);
                System.out.println("DIRECT_WRITE_IT_PASS database=" + source.databaseType() + " append upsert overwrite emptyOverwrite");
                assertEquals(2,BatchJdbcWriter.write(source,table,rows(RowFactory.create(1L,1,"old"),RowFactory.create(2L,2,"keep")),"APPEND",List.of(),Map.of(),null,false));
                assertCount(sql,target,2);
                var condition=new QueryFilter("region",QueryValueType.INTEGER,QueryFilterOperator.EQ,List.of(1));
                assertEquals(1,BatchJdbcWriter.write(source,table,rows(RowFactory.create(3L,1,"replacement")),"OVERWRITE",List.of(),Map.of(),condition,false));
                assertCount(sql,target,2);
                assertEquals(1, scalar(sql,"SELECT COUNT(*) FROM "+target+" WHERE "+id+"=2"));
                assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(source,table,rows(RowFactory.create(4L,null,"outside")),"OVERWRITE",List.of(),Map.of(),condition,false));
                assertCount(sql,target,2);
                assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(source,table,rows(),"OVERWRITE",List.of(),Map.of(),null,false));
                assertCount(sql,target,2);
                assertEquals(2,BatchJdbcWriter.write(source,table,rows(RowFactory.create(3L,1,"updated"),RowFactory.create(5L,3,"inserted")),"UPSERT",List.of("id"),Map.of(),null,false));
                assertCount(sql,target,3);
                assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(source,table,rows(RowFactory.create(8L,1,"a"),RowFactory.create(8L,1,"b")),"UPSERT",List.of("id"),Map.of(),null,false));
                assertCount(sql,target,3);
                // Staging permits duplicate ids; target constraint fails after DELETE, then entire transaction rolls back.
                assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(source,table,rows(RowFactory.create(9L,1,"a"),RowFactory.create(9L,1,"b")),"OVERWRITE",List.of(),Map.of(),null,false));
                assertCount(sql,target,3);
                assertEquals(0,BatchJdbcWriter.write(source,table,rows(),"OVERWRITE",List.of(),Map.of(),null,true));
                assertCount(sql,target,0);
                // The first attempt commits 500 staging rows before failing. Spark retries it;
                // only the winning attempt may become visible in the target.
                Dataset<Row> retrying = spark.range(1200).selectExpr("id", "1 AS region", "'retry' AS payload")
                        .mapPartitions((org.apache.spark.api.java.function.MapPartitionsFunction<Row, Row>)
                                BatchJdbcWriterIntegrationTest::retryIterator, Encoders.row(rows().schema()));
                assertEquals(1200, BatchJdbcWriter.write(source,table,retrying,"APPEND",List.of(),Map.of(),null,false));
                assertCount(sql,target,1200);
                if (source.databaseType()==RuntimeDatabaseType.POSTGRESQL) {
                    Dataset<Row> failing = spark.range(1200).selectExpr("id", "1 AS region", "'fail' AS payload")
                            .mapPartitions((org.apache.spark.api.java.function.MapPartitionsFunction<Row, Row>)
                                    BatchJdbcWriterIntegrationTest::failingIterator, Encoders.row(rows().schema()));
                    assertThrows(RunnerExecutionException.class, () -> BatchJdbcWriter.write(source,table,failing,"OVERWRITE",List.of(),Map.of(),null,false));
                    assertCount(sql,target,1200);
                    commitFaults(source,table,sql,target);
                    preservation(source);
                }
                System.out.println("BATCH_WRITE_IT_PASS database="+source.databaseType()+" append conditional nullScope emptyGuard upsert duplicateKeys rollback clearEmpty");
            } finally { sql.executeUpdate("DROP TABLE "+target); }
        }
        if (source.databaseType()==RuntimeDatabaseType.POSTGRESQL || source.databaseType()==RuntimeDatabaseType.MYSQL)
            spatial(source);
    }
    private static Iterator<Row> retryIterator(Iterator<Row> input) {
        return new Iterator<>() {
            int seen;
            public boolean hasNext() {
                if (org.apache.spark.TaskContext.get().partitionId() == 0
                        && org.apache.spark.TaskContext.get().attemptNumber() == 0 && seen == 550)
                    throw new IllegalStateException("intentional staging attempt failure");
                return input.hasNext();
            }
            public Row next() { seen++; return input.next(); }
        };
    }
    private static Iterator<Row> failingIterator(Iterator<Row> input) {
        return new Iterator<>() {
            int seen;
            public boolean hasNext() {
                if (seen == 550) throw new IllegalStateException("intentional persistent staging failure");
                return input.hasNext();
            }
            public Row next() { seen++; return input.next(); }
        };
    }
    private void preservation(RuntimeDataSource source) throws Exception {
        String name="dswr_"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
        var table=new TableIdentifier(source.connection().catalogName(),source.connection().schemaName(),name);
        String target=BuiltInDialects.registry().require("POSTGRESQL").qualifiedName(table);
        // Quote the full view name, not a suffix outside an already quoted identifier.
        String view=BuiltInDialects.registry().require("POSTGRESQL").qualifiedName(new TableIdentifier(table.catalog(),table.schema(),name+"_view"));
        try(var c=DriverManager.getConnection(source.connection().jdbcUrl(),SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()));var sql=c.createStatement()) {
            sql.executeUpdate("CREATE TABLE "+target+" (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, payload VARCHAR(40), flag INTEGER DEFAULT 7)");
            try {
                sql.executeUpdate("CREATE INDEX \""+name+"_idx\" ON "+target+" (payload)");
                sql.executeUpdate("CREATE VIEW "+view+" AS SELECT * FROM "+target);
                try {
                    var duplicates=spark.sql("SELECT 'same' payload UNION ALL SELECT 'same' payload");
                    assertEquals(2,BatchJdbcWriter.write(source,table,duplicates,"APPEND",List.of(),Map.of(),null,false));
                    assertCount(sql,target,2); // Legitimate APPEND duplicates must not be deduplicated.
                    assertEquals(2,scalar(sql,"SELECT COUNT(*) FROM "+target+" WHERE flag=7"));
                    assertEquals(1,BatchJdbcWriter.write(source,table,spark.sql("SELECT 'new' payload"),"OVERWRITE",List.of(),Map.of(),null,false));
                    assertCount(sql,view,1);
                    assertEquals(3,scalar(sql,"SELECT id FROM "+target));
                    assertEquals(2,scalar(sql,"SELECT COUNT(*) FROM pg_indexes WHERE tablename='"+name+"'"));
                    String child = BuiltInDialects.registry().require("POSTGRESQL").qualifiedName(
                            new TableIdentifier(table.catalog(), table.schema(), name + "_child"));
                    sql.executeUpdate("CREATE TABLE " + child + " (parent_id BIGINT REFERENCES " + target + "(id) ON DELETE CASCADE)");
                    try {
                        sql.executeUpdate("INSERT INTO " + child + " VALUES (3)");
                        assertThrows(RunnerExecutionException.class, () -> BatchJdbcWriter.write(source, table,
                                spark.sql("SELECT 'replacement' payload"), "OVERWRITE", List.of(), Map.of(), null, false));
                        assertCount(sql, target, 1);
                        assertCount(sql, child, 1);
                        assertEquals(1, BatchJdbcWriter.write(source, table, spark.sql("SELECT 'append allowed' payload"),
                                "APPEND", List.of(), Map.of(), null, false));
                        assertCount(sql, target, 2);
                        assertCount(sql, child, 1);
                    } finally { sql.executeUpdate("DROP TABLE " + child); }
                    System.out.println("BATCH_WRITE_PRESERVATION_PASS identity default index view appendDuplicates persistentLoadFailure");
                } finally { sql.executeUpdate("DROP VIEW "+view); }
            } finally { sql.executeUpdate("DROP TABLE "+target); }
        }
    }
    private void commitFaults(RuntimeDataSource source,TableIdentifier table,Statement sql,String target)throws Exception {
        Class.forName(BatchWriteFaultDriver.class.getName());
        var c=source.connection();
        var fault=new RuntimeDataSource(source.dataSourceId(),source.databaseType(),source.purposes(),
                new RuntimeJdbcConnection(BatchWriteFaultDriver.class.getName(),"jdbc:write-fault:"+c.jdbcUrl(),
                        c.catalogName(),c.schemaName(),c.username(),c.password(),c.properties()));
        try {
            BatchWriteFaultDriver.interruptAfterDelete=true;
            try {
                assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(fault,table,
                        rows(RowFactory.create(9999L,1,"cancel")),"OVERWRITE",List.of(),Map.of(),null,false));
            } finally { Thread.interrupted(); BatchWriteFaultDriver.interruptAfterDelete=false; }
            assertCount(sql,target,1200);
            BatchWriteFaultDriver.loseCommitAcknowledgement=true;
            var failure=assertThrows(RunnerExecutionException.class,()->BatchJdbcWriter.write(fault,table,
                    rows(RowFactory.create(9000L,1,"committed")),"OVERWRITE",List.of(),Map.of(),null,false));
            assertEquals("BATCH_WRITE_COMMIT_UNKNOWN",failure.code());
            assertCount(sql,target,1); // The DB committed, even though the client did not receive an acknowledgement.
            var error=new RunnerFailureClassifier().classify(failure,RunnerFailureContext.task(
                    cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase.WRITE));
            assertFalse(error.retryable());
            assertFalse(BatchWriteFaultDriver.ownedTables.isEmpty());
            BatchWriteFaultDriver.loseCommitAcknowledgement=false;
            BatchWriteFaultDriver.failCleanup=true;
            assertEquals(1,BatchJdbcWriter.write(fault,table,rows(RowFactory.create(9001L,1,"cleanup")),
                    "APPEND",List.of(),Map.of(),null,false));
            assertCount(sql,target,2);
            System.out.println("BATCH_WRITE_FAULT_PASS cancellationRollback commitUnknownNonRetryable cleanupDoesNotOverrideSuccess");
        } finally {
            BatchWriteFaultDriver.loseCommitAcknowledgement=false;
            BatchWriteFaultDriver.failCleanup=false;
            for(String owned:List.copyOf(BatchWriteFaultDriver.ownedTables)) {
                assertTrue(owned.contains("\"dsw_"));
                sql.executeUpdate("DROP TABLE "+owned);
                BatchWriteFaultDriver.ownedTables.remove(owned);
            }
        }
    }
    private void spatial(RuntimeDataSource source) throws Exception {
        var dialect=BuiltInDialects.registry().require(source.databaseType().name());
        var table=new TableIdentifier(source.connection().catalogName(),source.connection().schemaName(),
                "dswr_"+UUID.randomUUID().toString().replace("-", "").substring(0,16));
        String target=dialect.qualifiedName(table), id=dialect.quoteIdentifier("id"), geom=dialect.quoteIdentifier("geom");
        try(var c=DriverManager.getConnection(source.connection().jdbcUrl(),SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()));var sql=c.createStatement()) {
            sql.executeUpdate("CREATE TABLE "+target+" ("+id+" BIGINT PRIMARY KEY, "+geom+" "+
                    (source.databaseType()==RuntimeDatabaseType.POSTGRESQL?"geometry(Geometry,4326)":"GEOMETRY SRID 4326")+")");
            try {
                // A typed null Geometry, not a string column.
                Dataset<Row> input=spark.sql("SELECT id, ST_SetSRID(ST_GeomFromWKT(wkt),4326) geom FROM VALUES (1L,'POINT (30 40)'),(2L,CAST(NULL AS STRING)) AS t(id,wkt)");
                assertEquals(2,BatchJdbcWriter.write(source,table,input,"APPEND",List.of(),Map.of("geom",4326),null,false));
                assertEquals(4326,scalar(sql,"SELECT ST_SRID("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(30,scalar(sql,"SELECT ST_X("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(40,scalar(sql,"SELECT ST_Y("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(1,scalar(sql,"SELECT COUNT(*) FROM "+target+" WHERE "+geom+" IS NULL"));
                assertEquals(2,BatchJdbcWriter.write(source,table,input,"UPSERT",List.of("id"),Map.of("geom",4326),null,false));
                assertCount(sql,target,2);
                assertEquals(2,BatchJdbcWriter.write(source,table,input,"OVERWRITE",List.of(),Map.of("geom",4326),null,false));
                sql.executeUpdate("DELETE FROM "+target);
                DirectJdbcWriter.write(source,table,target,input,"APPEND",List.of(),Map.of("geom",4326));
                DirectJdbcWriter.write(source,table,target,input,"UPSERT",List.of("id"),Map.of("geom",4326));
                DirectJdbcWriter.write(source,table,target,input,"OVERWRITE",List.of(),Map.of("geom",4326));
                assertCount(sql,target,2);
                sdkSpatialRoundTrip(source,table);
                assertCount(sql,target,2);
                assertEquals(4326,scalar(sql,"SELECT ST_SRID("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(30,scalar(sql,"SELECT ST_X("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(40,scalar(sql,"SELECT ST_Y("+geom+") FROM "+target+" WHERE "+id+"=1"));
                assertEquals(1,scalar(sql,"SELECT COUNT(*) FROM "+target+" WHERE "+geom+" IS NULL"));
                System.out.println("BATCH_WRITE_SPATIAL_PASS database="+source.databaseType()+" null srid append upsert overwrite direct");
            } finally { sql.executeUpdate("DROP TABLE "+target); }
        }
    }
    private void sdkSpatialRoundTrip(RuntimeDataSource original, TableIdentifier table) {
        Set<DataSourcePurpose> purposes = Set.of(DataSourcePurpose.STORAGE, DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION);
        var source = new RuntimeDataSource(original.dataSourceId(), original.databaseType(), purposes, original.connection());
        var columns = List.of(
                new CanvasColumnSchema("id", PlatformDataType.LONG, null, null, null, false, null, true, false, null),
                new CanvasColumnSchema("geom", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                        new GeometryTypeDefinition(GeometryKind.POINT, CrsReference.epsg(4326), CoordinateDimension.XY)));
        var model = new MetadataModel(UUID.randomUUID(), table.table(), table.table(), 1, MetadataModelStatus.PUBLISHED,
                MetadataModelPhysicalTableMode.MANAGED, source.dataSourceId(), table.catalog(), table.schema(), table.table(), columns,
                List.of(new MetadataUniqueKey("test_pk", MetadataUniqueKeyType.PRIMARY_KEY, List.of("id"))));
        var metadata = new MetadataSnapshot(List.of(new MetadataDataSource(source.dataSourceId(), true, ConnectionKind.JDBC,
                CanvasJdbcDatabaseType.valueOf(source.databaseType().name()), purposes, List.of())), List.of(model));
        var payload = new SparkJarExecutionPayload(1, "example.SpatialAcceptance", List.of(), List.of(), List.of(
                new SparkJarExecutionPayload.ResourceBinding("source", SparkJarResourceType.MODEL, model.id(), null, SparkJarResourceAccessMode.READ),
                new SparkJarExecutionPayload.ResourceBinding("target", SparkJarResourceType.MODEL, model.id(), null, SparkJarResourceAccessMode.WRITE)),
                SparkJarExecutionPayload.TriggerType.MANUAL, null, null, SparkJarExecutionPayload.ExecutionPurpose.REAL);
        var now = java.time.Instant.now();
        var execution = new TaskExecutionManifest.Execution(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, now, now.plusSeconds(600));
        var manifest = new TaskExecutionManifest(TaskExecutionManifest.CURRENT_MANIFEST_VERSION, execution, null, metadata, List.of(source),
                null, null, List.of(), null, ExecutionTaskType.SPARK_JAR, null, payload, null);
        var context = new SparkJarJobContextImpl(spark, manifest, new ObjectMapper().findAndRegisterModules(), ignored -> {}, ignored -> {});
        // Lazy JDBC input reads the same table: staging must finish before the target DELETE.
        var dataset = context.models().read("source");
        assertEquals(2, context.models().write("target", dataset)
                .mode(cn.superhuang.datascalpel.sdk.ModelWriteMode.OVERWRITE)
                .batchWrite(cn.superhuang.datascalpel.sdk.BatchWriteOptions.atomic())
                .map("id", "id").map("geom", "geom").execute().affectedRows());
        assertEquals(2, context.models().write("target", context.models().read("source"))
                .mode(cn.superhuang.datascalpel.sdk.ModelWriteMode.UPSERT)
                .map("id", "id").map("geom", "geom").execute().affectedRows());
        System.out.println("BATCH_WRITE_SDK_SPATIAL_PASS database=" + source.databaseType());
    }
    private static void assertCount(Statement statement,String table,long expected)throws Exception { assertEquals(expected,scalar(statement,"SELECT COUNT(*) FROM "+table)); }
    private static long scalar(Statement statement,String sql)throws Exception { try(var result=statement.executeQuery(sql)){result.next();return result.getLong(1);} }
}
