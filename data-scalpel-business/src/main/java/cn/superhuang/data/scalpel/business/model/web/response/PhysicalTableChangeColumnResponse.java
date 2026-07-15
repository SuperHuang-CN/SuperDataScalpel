package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;

import java.util.UUID;

public record PhysicalTableChangeColumnResponse(
        UUID columnId,
        String name,
        TableColumnType type,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable
) {
    static PhysicalTableChangeColumnResponse from(TableColumnDefinition column) {
        if (column == null) {
            return null;
        }
        return new PhysicalTableChangeColumnResponse(
                column.columnId(), column.name(), column.type(), column.length(), column.precision(), column.scale(), column.nullable()
        );
    }
}
