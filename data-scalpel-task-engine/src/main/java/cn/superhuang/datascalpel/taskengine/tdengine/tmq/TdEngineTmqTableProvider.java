package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

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

public final class TdEngineTmqTableProvider implements TableProvider, DataSourceRegister {
    @Override
    public StructType inferSchema(CaseInsensitiveStringMap options) {
        throw new IllegalArgumentException("TDengine TMQ source requires an explicit schema");
    }

    @Override
    public boolean supportsExternalMetadata() {
        return true;
    }

    @Override
    public Table getTable(StructType schema, Transform[] partitioning, Map<String, String> properties) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("TDengine TMQ source schema is required");
        }
        CaseInsensitiveStringMap options = new CaseInsensitiveStringMap(properties);
        TdEngineTmqOptions.from(options);
        return new TmqTable(schema);
    }

    @Override
    public String shortName() {
        return "datascalpel-tdengine-tmq";
    }

    private record TmqTable(StructType schema) implements SupportsRead {
        @Override
        public String name() {
            return "DataScalpel TDengine TMQ";
        }

        @Override
        public Set<TableCapability> capabilities() {
            return Set.of(TableCapability.MICRO_BATCH_READ);
        }

        @Override
        public ScanBuilder newScanBuilder(CaseInsensitiveStringMap runtimeOptions) {
            return () -> new TdEngineTmqScan(schema, TdEngineTmqOptions.from(runtimeOptions));
        }
    }
}
