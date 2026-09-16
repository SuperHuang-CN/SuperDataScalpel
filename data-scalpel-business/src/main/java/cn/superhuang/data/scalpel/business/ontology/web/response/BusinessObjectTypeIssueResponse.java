package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "业务对象类型配置诊断。")
public record BusinessObjectTypeIssueResponse(
        @Schema(description = "稳定诊断编码。") String code,
        @Schema(description = "配置中的字段路径。") String path,
        @Schema(description = "中文诊断信息。") String message,
        @Schema(description = "是否会阻止当前对象预览。") boolean blocking
) {
}
