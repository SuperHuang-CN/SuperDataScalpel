package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableMetadata;

import java.util.List;

public record TableMetadataResponse(
        TableSummaryResponse table,
        List<ColumnMetadataResponse> columns,
        PrimaryKeyResponse primaryKey,
        List<IndexMetadataResponse> indexes
) {
    public static TableMetadataResponse from(TableMetadata metadata) {
        return new TableMetadataResponse(
                TableSummaryResponse.from(metadata.table()),
                metadata.columns().stream().map(ColumnMetadataResponse::from).toList(),
                PrimaryKeyResponse.from(metadata.primaryKey()),
                metadata.indexes().stream().map(IndexMetadataResponse::from).toList()
        );
    }
}
