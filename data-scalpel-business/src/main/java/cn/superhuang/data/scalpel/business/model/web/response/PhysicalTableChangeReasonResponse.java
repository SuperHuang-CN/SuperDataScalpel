package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeReason;
import cn.superhuang.data.scalpel.dialect.model.TableChangeReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "方言给出的策略、风险或不支持原因")
public record PhysicalTableChangeReasonResponse(
        @Schema(description = "物理表变更风险或限制的稳定原因码，供客户端判断处理方式") TableChangeReasonCode code,
        @Schema(description = "面向技术用户和 AI 的具体原因说明") String message
) {
    static PhysicalTableChangeReasonResponse from(TableChangeReason reason) {
        return new PhysicalTableChangeReasonResponse(reason.code(), reason.message());
    }
}
