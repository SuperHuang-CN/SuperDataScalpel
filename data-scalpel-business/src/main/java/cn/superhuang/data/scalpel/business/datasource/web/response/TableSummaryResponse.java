package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableSummary;

public record TableSummaryResponse(TableIdentifierResponse identifier, String type, String comment) {
    static TableSummaryResponse from(TableSummary table) {
        return new TableSummaryResponse(TableIdentifierResponse.from(table.identifier()), table.type(), table.comment());
    }
}
