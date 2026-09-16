package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "当前业务对象类型定义的只读校验结果。")
public record BusinessObjectTypeValidationResponse(
        @Schema(description = "当前配置是否可用于读取主来源并预览具体对象。") boolean canPreview,
        @Schema(description = "配置诊断；保存允许不完整配置，但预览会受阻断诊断限制。") List<BusinessObjectTypeIssueResponse> issues
) {
    public BusinessObjectTypeValidationResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
