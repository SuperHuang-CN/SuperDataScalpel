package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据库表结构、主键、索引和安全唯一键元数据")
public record TableMetadataResponse(
        @Schema(description = "表或视图摘要") TableSummaryResponse table,
        @Schema(description = "按数据库列顺序返回的字段元数据") List<ColumnMetadataResponse> columns,
        @Schema(description = "主键元数据；无主键时为空") PrimaryKeyResponse primaryKey,
        @Schema(description = "数据库索引列表") List<IndexMetadataResponse> indexes,
        @Schema(description = "可用于唯一标识记录的安全唯一键列表") List<UniqueKeyMetadataResponse> uniqueKeys
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
