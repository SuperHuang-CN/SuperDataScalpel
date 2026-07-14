package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;

public record NamespaceResponse(String catalog, String schema, String displayName, boolean defaultNamespace) {
    public static NamespaceResponse from(NamespaceInfo namespace) {
        return new NamespaceResponse(
                namespace.catalog(),
                namespace.schema(),
                namespace.displayName(),
                namespace.defaultNamespace()
        );
    }
}
