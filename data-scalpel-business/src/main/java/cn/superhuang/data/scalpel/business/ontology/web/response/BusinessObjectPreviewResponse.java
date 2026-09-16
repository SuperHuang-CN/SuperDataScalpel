package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "一个具体业务对象按当前定义组合出的只读结果。")
public record BusinessObjectPreviewResponse(
        @Schema(description = "当前具体对象身份。") BusinessObjectIdentityResponse object,
        @Schema(description = "按属性展示顺序排列的读取结果。") List<BusinessObjectPropertyValueResponse> properties,
        @Schema(description = "本次读取发现的来源或关联异常；不代表全量数据检查结果。") List<BusinessObjectTypeIssueResponse> diagnostics,
        @Schema(description = "本次读取发起时间。") Instant queriedAt
) {
    public BusinessObjectPreviewResponse {
        properties = properties == null ? List.of() : List.copyOf(properties);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
