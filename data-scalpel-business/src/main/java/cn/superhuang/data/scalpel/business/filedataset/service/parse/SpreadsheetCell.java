package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

/** A typed spreadsheet cell value before it is aligned with the configured header row. */
record SpreadsheetCell(Object value, LogicalType logicalType) {

    boolean isEmpty() {
        return value == null || value instanceof String string && string.isBlank();
    }
}
