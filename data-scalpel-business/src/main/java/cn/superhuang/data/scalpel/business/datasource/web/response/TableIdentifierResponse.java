package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

public record TableIdentifierResponse(String catalog, String schema, String table) {
    static TableIdentifierResponse from(TableIdentifier identifier) {
        return new TableIdentifierResponse(identifier.catalog(), identifier.schema(), identifier.table());
    }
}
