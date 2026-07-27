package cn.superhuang.data.scalpel.shapefile.internal;

import java.nio.charset.Charset;
import java.util.List;

public record DbfTableDefinition(
        long recordCount,
        int headerLength,
        int recordLength,
        Charset charset,
        List<DbfFieldDefinition> fields) {
    public DbfTableDefinition {
        fields = List.copyOf(fields);
    }
}
