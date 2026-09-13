package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据库表主键元数据")
public record PrimaryKeyResponse(
        @Schema(description = "主键约束名称；数据库未提供名称时可为空") String name,
        @Schema(description = "主键列名，顺序与数据库定义一致") List<String> columns
) {
    static PrimaryKeyResponse from(PrimaryKeyMetadata key) {
        return key == null ? null : new PrimaryKeyResponse(key.name(), key.columns());
    }
}
