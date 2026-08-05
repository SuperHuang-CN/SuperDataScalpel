package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;

import java.util.List;

public record TableMetadataResponse(
        TableSummaryResponse table,
        List<ColumnMetadataResponse> columns,
        PrimaryKeyResponse primaryKey,
        List<IndexMetadataResponse> indexes,
        List<UniqueKeyMetadataResponse> uniqueKeys
) {
    public static TableMetadataResponse from(TableMetadata metadata, DatabaseDialect dialect) {
        return new TableMetadataResponse(
                TableSummaryResponse.from(metadata.table()),
                metadata.columns().stream().map(column -> ColumnMetadataResponse.from(column, dialect)).toList(),
                PrimaryKeyResponse.from(metadata.primaryKey()),
                metadata.indexes().stream().map(IndexMetadataResponse::from).toList(),
                metadata.uniqueKeys().stream().map(UniqueKeyMetadataResponse::from).toList()
        );
    }
}
