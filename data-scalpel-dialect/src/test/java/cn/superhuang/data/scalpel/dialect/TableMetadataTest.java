package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableMetadataTest {

    @Test
    void representsAnAbsentPrimaryKeyAsAnEmptyMetadataValue() {
        TableMetadata metadata = new TableMetadata(
                new TableSummary(new TableIdentifier("database", "public", "orders"), "TABLE", null),
                List.of(), null, List.of()
        );

        assertEquals(new PrimaryKeyMetadata(null, List.of()), metadata.primaryKey());
    }
}
