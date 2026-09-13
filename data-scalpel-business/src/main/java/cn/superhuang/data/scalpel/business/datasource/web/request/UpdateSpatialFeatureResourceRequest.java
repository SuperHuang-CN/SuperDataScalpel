package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "修改空间要素资源的显示信息和启停状态；远端标识与输出坐标系需重新登记才能改变")
public record UpdateSpatialFeatureResourceRequest(
        @Schema(description = "资源显示名称")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "是否允许 Canvas 或其他业务引用此空间资源")
        @NotNull Boolean enabled
) {
}
