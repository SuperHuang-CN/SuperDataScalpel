package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "SQL 服务测试发现的一条可定位问题。")

public record SqlServiceTestProblem(
        @Schema(description = "稳定问题码，用于区分 SQL、参数、模型引用和结果结构问题。")
        String code,
        @Schema(description = "说明 SQL、参数、模型引用或结果结构为何未通过测试的可读信息。")
        String message,
        @Schema(description = "问题所属对象或字段路径；无法定位到具体字段时为空。")
        String subject
) {
}
