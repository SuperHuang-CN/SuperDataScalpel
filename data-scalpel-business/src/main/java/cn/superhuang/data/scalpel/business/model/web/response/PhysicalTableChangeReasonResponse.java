package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeReason;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReasonCode;

public record PhysicalTableChangeReasonResponse(TableChangeReasonCode code, String message) {
    static PhysicalTableChangeReasonResponse from(TableChangeReason reason) {
        return new PhysicalTableChangeReasonResponse(reason.code(), reason.message());
    }
}
