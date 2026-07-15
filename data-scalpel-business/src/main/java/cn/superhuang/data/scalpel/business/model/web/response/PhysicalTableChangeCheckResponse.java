package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;

import java.util.List;

public record PhysicalTableChangeCheckResponse(
        TableChangeCheckType type,
        List<String> columnNames,
        Integer lengthLimit,
        Integer precisionLimit,
        Integer scaleLimit,
        String expectedFingerprint,
        String description
) {
    static PhysicalTableChangeCheckResponse from(TableChangeCheck check) {
        return new PhysicalTableChangeCheckResponse(
                check.type(), check.columnNames(), check.lengthLimit(), check.precisionLimit(), check.scaleLimit(),
                check.expectedFingerprint() == null ? null : check.expectedFingerprint().value(), check.description()
        );
    }
}
