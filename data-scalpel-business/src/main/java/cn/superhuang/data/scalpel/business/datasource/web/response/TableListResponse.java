package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableList;

import java.util.List;

public record TableListResponse(List<TableSummaryResponse> tables, boolean truncated) {
    public static TableListResponse from(TableList tableList) {
        return new TableListResponse(
                tableList.tables().stream().map(TableSummaryResponse::from).toList(),
                tableList.truncated()
        );
    }
}
