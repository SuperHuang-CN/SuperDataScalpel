package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "模型物理表只读查询的排序项")
public record DataModelDataQueryOrderInput(
        @Schema(description = "可排序模型字段编码；服务端按字段白名单解析") @NotBlank String field,
        @Schema(description = "ASC 升序或 DESC 降序") @NotNull DataModelDataQuerySortDirection direction
) {
}
