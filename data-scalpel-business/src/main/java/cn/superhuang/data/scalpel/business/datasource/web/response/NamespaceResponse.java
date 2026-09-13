package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据库可浏览命名空间")
public record NamespaceResponse(
        @Schema(description = "Catalog 名称；数据库不使用 Catalog 时为空") String catalog,
        @Schema(description = "Schema 名称；数据库不使用 Schema 时为空") String schema,
        @Schema(description = "供界面展示的完整命名空间名称") String displayName,
        @Schema(description = "是否为该连接建议默认使用的命名空间") boolean defaultNamespace
) {
    public static NamespaceResponse from(NamespaceInfo namespace) {
        return new NamespaceResponse(
                namespace.catalog(),
                namespace.schema(),
                namespace.displayName(),
                namespace.defaultNamespace()
        );
    }
}
