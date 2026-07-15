package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;

import java.util.List;

public record PhysicalTableChangeExecutionOptionResponse(
        TableChangeExecutionMode mode,
        TableDdlAtomicity atomicity,
        List<String> statements
) {
    static PhysicalTableChangeExecutionOptionResponse from(TableChangeExecutionOption option) {
        return new PhysicalTableChangeExecutionOptionResponse(option.mode(), option.atomicity(), option.statements());
    }
}
