package cn.superhuang.data.scalpel.business.quality.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.util.List;
import java.util.UUID;

@Schema(description = "REFERENCE_EXISTS 规则当前引用的目标模型及目标字段摘要。")
public record ModelQualityRuleReferenceTargetResponse(
        @Schema(description = "引用目标模型 UUID。")
        UUID id,
        @Schema(description = "引用目标模型稳定编码。")
        String code,
        @Schema(description = "引用目标模型展示名称。")
        String name,
        @Schema(description = "引用目标模型当前生命周期状态，仅供展示；保存规则不要求特定状态。运行准备会校验模型仍存在、物理字段快照可读取且其绑定数据源可供 Spark 读取。")
        DataModelStatus status,
        @Schema(description = "规则映射实际引用的目标字段，按映射顺序返回；不包含目标模型的其他字段。")
        List<ModelQualityRuleFieldResponse> fields
) {
    public static ModelQualityRuleReferenceTargetResponse from(
            DataModel model,
            List<ModelQualityRuleFieldResponse> fields
    ) {
        return new ModelQualityRuleReferenceTargetResponse(
                model.getId(), model.getCode(), model.getName(), model.getStatus(), fields
        );
    }
}
