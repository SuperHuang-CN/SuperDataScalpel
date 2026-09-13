package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据库索引元数据")
public record IndexMetadataResponse(
        @Schema(description = "数据库索引名称。") String name,
        @Schema(description = "索引是否声明唯一") boolean unique,
        @Schema(description = "索引列名，顺序与数据库定义一致") List<String> columns,
        @Schema(description = "该索引是否满足受控写入时的安全唯一键要求") boolean usableAsUniqueKey
) {
    static IndexMetadataResponse from(IndexMetadata index) {
        return new IndexMetadataResponse(
                index.name(), index.unique(), index.columns(), index.usableAsUniqueKey());
    }
}
