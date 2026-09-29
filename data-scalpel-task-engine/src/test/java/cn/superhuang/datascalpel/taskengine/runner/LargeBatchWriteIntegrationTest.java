package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.*;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic capacity benchmark, explicitly separate from registered business-data acceptance. */
@EnabledIfEnvironmentVariable(named="DATASCALPEL_WRITE_IT_LARGE_ROWS", matches="[1-9][0-9]*")
class LargeBatchWriteIntegrationTest {
    @Test void compareDirectAndAtomicMillionRows()throws Exception {
        int rows=Integer.parseInt(System.getenv("DATASCALPEL_WRITE_IT_LARGE_ROWS"));
        assertTrue(rows<=1_000_000,"Keep the shared development benchmark bounded");
        var spark=SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[4]")
                .appName("batch-write-benchmark").config("spark.ui.enabled","false")
                .config("spark.driver.host","127.0.0.1").getOrCreate());
        spark.sparkContext().setLogLevel("WARN");
        try {
            var dataset=spark.range(0,rows,1,4).selectExpr("id", "CAST(id % 100 AS INT) AS region", "repeat(md5(CAST(id AS STRING)),4) AS payload");
            for(var db:new ObjectMapper().readTree(System.getenv("DATASCALPEL_WRITE_IT_DATABASES"))) {
                if (!Set.of("POSTGRESQL","MYSQL").contains(db.path("type").asText())) continue;
                var source=new RuntimeDataSource(UUID.randomUUID(),RuntimeDatabaseType.valueOf(db.path("type").asText()),Set.of(DataSourcePurpose.DISTRIBUTION),
                        new RuntimeJdbcConnection(db.path("driver").asText(),db.path("url").asText(),db.path("catalog").asText(null),db.path("schema").asText(null),db.path("user").asText(),db.path("password").asText(),Map.of()));
                var dialect=SpatialJdbcRuntimeSupport.dialect(source);
                var table=new TableIdentifier(source.connection().catalogName(),source.connection().schemaName(),"dswr_perf_"+UUID.randomUUID().toString().replace("-", "").substring(0,12));
                String target=dialect.qualifiedName(table);
                try(var c=DriverManager.getConnection(source.connection().jdbcUrl(),SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()));var sql=c.createStatement()) {
                    sql.executeUpdate("CREATE TABLE "+target+" (id BIGINT PRIMARY KEY, region INTEGER, payload VARCHAR(160))");
                    try {
                        long start=System.nanoTime();
                        DirectJdbcWriter.append(source,target,dataset);
                        long direct=(System.nanoTime()-start)/1_000_000;
                        start=System.nanoTime();
                        assertEquals(rows,BatchJdbcWriter.write(source,table,dataset,"OVERWRITE",List.of(),Map.of(),null,false));
                        long overwrite=(System.nanoTime()-start)/1_000_000;
                        try(var result=sql.executeQuery("SELECT COUNT(*) FROM "+target)){result.next();assertEquals(rows,result.getLong(1));}
                        start=System.nanoTime();
                        assertEquals(rows,BatchJdbcWriter.write(source,table,dataset,"UPSERT",List.of("id"),Map.of(),null,false));
                        long upsert=(System.nanoTime()-start)/1_000_000;
                        System.out.println("LARGE_BATCH_WRITE_PASS database="+source.databaseType()+" rows="+rows+" directAppendMs="+direct+" atomicOverwriteMs="+overwrite+" atomicUpsertMs="+upsert);
                    } finally {sql.executeUpdate("DROP TABLE "+target);}
                }
            }
        } finally {spark.stop();}
    }
}
