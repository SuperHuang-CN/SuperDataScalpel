package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import cn.superhuang.data.scalpel.dialect.model.UniqueKeyKind;
import cn.superhuang.data.scalpel.dialect.model.UniqueKeyMetadata;
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

    @Test
    void exposesOnlySafeUniqueKeysAndKeepsThePrimaryKeyFirst() {
        TableMetadata metadata = new TableMetadata(
                new TableSummary(new TableIdentifier("database", "public", "orders"), "TABLE", null),
                List.of(),
                new PrimaryKeyMetadata("orders_pkey", List.of("tenant_id", "order_id")),
                List.of(
                        new IndexMetadata("orders_pkey", true, List.of("tenant_id", "order_id"), true),
                        new IndexMetadata("uk_external", true, List.of("external_id"), true),
                        new IndexMetadata("uk_expression", true, List.of("normalized_code"), false),
                        new IndexMetadata("idx_status", false, List.of("status"), false)
                )
        );

        assertEquals(List.of(
                new UniqueKeyMetadata(
                        "orders_pkey", UniqueKeyKind.PRIMARY_KEY, List.of("tenant_id", "order_id")),
                new UniqueKeyMetadata(
                        "uk_external", UniqueKeyKind.UNIQUE_INDEX, List.of("external_id"))
        ), metadata.uniqueKeys());
    }
}
