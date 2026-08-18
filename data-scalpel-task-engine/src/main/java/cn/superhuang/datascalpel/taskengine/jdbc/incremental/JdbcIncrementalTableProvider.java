package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import org.apache.spark.sql.connector.catalog.SupportsRead;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.connector.catalog.TableProvider;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.sources.DataSourceRegister;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

import java.util.Map;
import java.util.Set;

public final class JdbcIncrementalTableProvider implements TableProvider, DataSourceRegister {
    @Override
    public StructType inferSchema(CaseInsensitiveStringMap options) {
        throw new IllegalArgumentException("JDBC incremental source requires an explicit schema");
    }

    @Override
    public boolean supportsExternalMetadata() { return true; }

    @Override
    public Table getTable(StructType schema, Transform[] partitioning, Map<String, String> properties) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("JDBC incremental source schema is required");
        }
        JdbcIncrementalOptions options = JdbcIncrementalOptions.from(new CaseInsensitiveStringMap(properties));
        return new IncrementalTable(schema, options);
    }

    @Override
    public String shortName() { return "datascalpel-jdbc-incremental"; }

    private record IncrementalTable(StructType schema, JdbcIncrementalOptions options) implements SupportsRead {
        @Override
        public String name() { return "DataScalpel JDBC incremental"; }

        @Override
        public Set<TableCapability> capabilities() { return Set.of(TableCapability.MICRO_BATCH_READ); }

        @Override
        public ScanBuilder newScanBuilder(CaseInsensitiveStringMap runtimeOptions) {
            return () -> new JdbcIncrementalScan(schema, JdbcIncrementalOptions.from(runtimeOptions));
        }
    }
}
