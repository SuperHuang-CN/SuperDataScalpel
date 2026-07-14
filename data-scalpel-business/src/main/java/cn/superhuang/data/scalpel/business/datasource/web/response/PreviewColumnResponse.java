package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.PreviewColumn;

public record PreviewColumnResponse(String name, String nativeType, String logicalType) {
    static PreviewColumnResponse from(PreviewColumn column) {
        return new PreviewColumnResponse(column.name(), column.nativeType(), column.logicalType().name());
    }
}
