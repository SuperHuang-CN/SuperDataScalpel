package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;

public record PhysicalTableDifferenceResponse(
        String column,
        TableStructureDifferenceType type,
        String expected,
        String actual
) {
    static PhysicalTableDifferenceResponse from(TableStructureDifference difference) {
        return new PhysicalTableDifferenceResponse(
                difference.column(), difference.type(), difference.expected(), difference.actual()
        );
    }
}
