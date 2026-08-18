package cn.superhuang.data.scalpel.business.quality.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.util.List;
import java.util.UUID;

public record ModelQualityRuleReferenceTargetResponse(
        UUID id,
        String code,
        String name,
        DataModelStatus status,
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
