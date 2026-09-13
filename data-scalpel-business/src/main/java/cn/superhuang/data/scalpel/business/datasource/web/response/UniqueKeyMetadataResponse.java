package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.UniqueKeyMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "可用于唯一标识记录的约束或索引")
public record UniqueKeyMetadataResponse(
        @Schema(description = "唯一约束或索引名称") String name,
        @Schema(description = "唯一键来源类型，例如 PRIMARY_KEY、UNIQUE_CONSTRAINT 或 UNIQUE_INDEX") String type,
        @Schema(description = "组成唯一键的列名，顺序与数据库定义一致") List<String> columns
) {
    static UniqueKeyMetadataResponse from(UniqueKeyMetadata key) {
        return new UniqueKeyMetadataResponse(key.name(), key.kind().name(), key.columns());
    }
}
