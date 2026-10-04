package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.*;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** REAL execution on the user's registered source; only a randomly owned sink is mutable. */
@EnabledIfEnvironmentVariable(named="DATASCALPEL_WRITE_IT_REGISTERED_MODEL", matches=".+")
class RegisteredDataWriteIntegrationTest {
    @Test void canvasAndSdkRoundTripRegisteredData() throws Exception {
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        var document=mapper.readTree(System.getenv("DATASCALPEL_WRITE_IT_REGISTERED_MODEL"));
        var m=document.path("model");
        var db=mapper.readTree(System.getenv("DATASCALPEL_WRITE_IT_DATABASES")).get(0);
        UUID sourceId=UUID.fromString(m.path("storageDataSourceId").asText());
        String database=m.path("catalogName").asText();
        RuntimeDataSource runtime=new RuntimeDataSource(sourceId,RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.STORAGE,DataSourcePurpose.DISTRIBUTION),
                new RuntimeJdbcConnection("org.postgresql.Driver","jdbc:postgresql://192.168.5.102:15432/"+database,
                        database,m.path("schemaName").asText(),db.path("user").asText(),db.path("password").asText(),Map.of()));
        List<CanvasColumnSchema> columns=new ArrayList<>();
        for(var f:document.path("fields")) columns.add(new CanvasColumnSchema(f.path("code").asText(),
                PlatformDataType.valueOf(f.path("fieldType").asText()),nullableInt(f,"length"),nullableInt(f,"precision"),
                nullableInt(f,"scale"),f.path("nullable").asBoolean(),null,false,false,null,
                f.path("geometry").isNull()?null:mapper.treeToValue(f.path("geometry"),GeometryTypeDefinition.class)));
        MetadataModel source=new MetadataModel(UUID.fromString(m.path("id").asText()),m.path("code").asText(),m.path("name").asText(),
                m.path("schemaVersion").asInt(),MetadataModelStatus.PUBLISHED,MetadataModelPhysicalTableMode.MANAGED,sourceId,
                database,m.path("schemaName").asText(),m.path("physicalTableName").asText(),columns);
        String ownedName="dswr_real_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        MetadataModel target=new MetadataModel(UUID.randomUUID(),ownedName,ownedName,1,MetadataModelStatus.PUBLISHED,
                MetadataModelPhysicalTableMode.MANAGED,sourceId,database,source.schemaName(),ownedName,columns);
        var dialect=SpatialJdbcRuntimeSupport.dialect(runtime);
        String input=dialect.qualifiedName(new TableIdentifier(database,source.schemaName(),source.physicalTableName()));
        String output=dialect.qualifiedName(new TableIdentifier(database,target.schemaName(),ownedName));
        try(var connection=DriverManager.getConnection(runtime.connection().jdbcUrl(),SpatialJdbcRuntimeSupport.jdbcProperties(runtime.connection()));
            var sql=connection.createStatement()) {
            long original=count(sql,input);
            assertTrue(original>0,"Registered source must contain real data");
            sql.executeUpdate("CREATE TABLE "+output+" (LIKE "+input+" INCLUDING ALL)");
            try {
                var metadata=new MetadataSnapshot(List.of(new MetadataDataSource(sourceId,true,ConnectionKind.JDBC,
                        CanvasJdbcDatabaseType.POSTGRESQL,Set.of(DataSourcePurpose.STORAGE,DataSourcePurpose.DISTRIBUTION),List.of())),List.of(source,target));
                var layout=new CanvasNodeLayout(0.0,0.0,240.0,120.0);
                String inputId=UUID.randomUUID().toString(),outputId=UUID.randomUUID().toString();
                var mappings=columns.stream().map(c->new JdbcColumnMapping(c.name(),c.name())).toList();
                var definition=new CanvasDefinition(CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(new ModelInputNodeDefinition(inputId,"source",layout,new ModelInputConfiguration(source.id().toString())),
                                new ModelOutputNodeDefinition(outputId,"owned target",layout,new ModelOutputConfiguration(List.of(
                                        new ModelOutputWrite(UUID.randomUUID().toString(),source.code(),target.id().toString(),JdbcWriteMode.OVERWRITE,
                                                mappings,new BatchWriteOptions(null,false)))))),
                        List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(),inputId,outputId)));
                Instant now=Instant.now();
                var execution=new TaskExecutionManifest.Execution(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,1,now,now.plusSeconds(1800));
                var manifest=new TaskExecutionManifest(TaskExecutionManifest.CURRENT_MANIFEST_VERSION,execution,
                        new TaskDefinition(TaskType.CANVAS,definition),metadata,List.of(runtime));
                long start=System.nanoTime();
                var result=new CanvasTaskExecutor().execute(manifest);
                assertEquals(TaskExecutionState.SUCCESS,result.state(),()->String.valueOf(result.error()));
                assertEquals(original,result.affectedRows());
                assertSameData(sql,input,output,columns);
                System.out.println("REGISTERED_CANVAS_REAL_PASS model="+source.code()+" rows="+original+" durationMs="+(System.nanoTime()-start)/1_000_000);
                var spark=SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]")
                        .appName("registered-sdk-write-it").config("spark.ui.enabled","false")
                        .config("spark.driver.host","127.0.0.1").getOrCreate());
                try {
                    var payload=new SparkJarExecutionPayload(1,"example.AcceptanceJob",List.of(),List.of(),List.of(
                            new SparkJarExecutionPayload.ResourceBinding("source",SparkJarResourceType.MODEL,source.id(),null,SparkJarResourceAccessMode.READ),
                            new SparkJarExecutionPayload.ResourceBinding("target",SparkJarResourceType.MODEL,target.id(),null,SparkJarResourceAccessMode.WRITE)),
                            SparkJarExecutionPayload.TriggerType.MANUAL,null,null,SparkJarExecutionPayload.ExecutionPurpose.REAL);
                    var jarManifest=new TaskExecutionManifest(TaskExecutionManifest.CURRENT_MANIFEST_VERSION,execution,null,metadata,List.of(runtime),
                            null,null,List.of(),null,ExecutionTaskType.SPARK_JAR,null,payload,null);
                    var context=new SparkJarJobContextImpl(spark,jarManifest,mapper,ignored->{},ignored->{});
                    var data=context.models().read("source");
                    var writer=context.models().write("target",data).mode(cn.superhuang.datascalpel.sdk.ModelWriteMode.OVERWRITE)
                            .batchWrite(cn.superhuang.datascalpel.sdk.BatchWriteOptions.atomic());
                    columns.forEach(c->writer.map(c.name(),c.name()));
                    assertEquals(original,writer.execute().affectedRows());
                    assertSameData(sql,input,output,columns);
                    var direct=context.models().write("target",data).mode(cn.superhuang.datascalpel.sdk.ModelWriteMode.OVERWRITE);
                    columns.forEach(c->direct.map(c.name(),c.name()));
                    assertEquals(original,direct.execute().affectedRows());
                    assertSameData(sql,input,output,columns);
                    var trialPayload=new SparkJarExecutionPayload(1,"example.AcceptanceJob",List.of(),List.of(),payload.resourceBindings(),
                            SparkJarExecutionPayload.TriggerType.MANUAL,null,null,SparkJarExecutionPayload.ExecutionPurpose.TRIAL);
                    var trialManifest=new TaskExecutionManifest(TaskExecutionManifest.CURRENT_MANIFEST_VERSION,execution,null,metadata,List.of(runtime),
                            null,null,List.of(),null,ExecutionTaskType.SPARK_JAR,null,trialPayload,null);
                    var trialContext=new SparkJarJobContextImpl(spark,trialManifest,mapper,ignored->{},ignored->{});
                    var trialWriter=trialContext.models().write("target",data).mode(cn.superhuang.datascalpel.sdk.ModelWriteMode.OVERWRITE)
                            .batchWrite(cn.superhuang.datascalpel.sdk.BatchWriteOptions.atomic());
                    columns.forEach(c->trialWriter.map(c.name(),c.name()));
                    trialWriter.execute();
                    assertSameData(sql,input,output,columns);
                    assertEquals(original,count(sql,input));
                    System.out.println("REGISTERED_SDK_REAL_PASS model="+source.code()+" rows="+original+" exactMultiset=true directCompatibility=true trialNonMutating=true");
                } finally { spark.stop(); }
            } finally { sql.executeUpdate("DROP TABLE "+output); }
        }
    }
    private static Integer nullableInt(com.fasterxml.jackson.databind.JsonNode node,String key) { return node.path(key).isNull()?null:node.path(key).intValue(); }
    private static long count(Statement sql,String table)throws Exception { try(var rs=sql.executeQuery("SELECT COUNT(*) FROM "+table)){rs.next();return rs.getLong(1);} }
    private static void assertSameData(Statement sql,String input,String output,List<CanvasColumnSchema> columns)throws Exception {
        String projection=columns.stream().map(c->{String q="\""+c.name().replace("\"","\"\"")+"\"";
            return c.fieldType()==PlatformDataType.GEOMETRY?"encode(ST_AsEWKB("+q+"),'hex')":q;}).collect(java.util.stream.Collectors.joining(","));
        try(var rs=sql.executeQuery("SELECT COUNT(*) FROM ((SELECT "+projection+" FROM "+input+" EXCEPT ALL SELECT "+projection+" FROM "+output+") UNION ALL (SELECT "+projection+" FROM "+output+" EXCEPT ALL SELECT "+projection+" FROM "+input+")) diff")) {
            rs.next(); assertEquals(0,rs.getLong(1),"All scalar values, geometry bytes/SRID and duplicate multiplicity must match");
        }
    }
}
