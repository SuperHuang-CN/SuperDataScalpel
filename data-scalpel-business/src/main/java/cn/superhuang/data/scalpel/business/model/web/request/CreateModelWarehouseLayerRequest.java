package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "创建全局扁平数仓分层及其建模建议规则。只有分层编码要求全局唯一；名称、模型编码前缀和输入规划规则可以与其他分层相同。")
public record CreateModelWarehouseLayerRequest(
        @Schema(description = "全局唯一分层编码；服务端保存为大写")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,31}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "分层显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "分层定位、数据粒度或建模职责说明") @Size(max = 500) String description,
        @Schema(description = "界面展示颜色，格式为 #RRGGBB")
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "颜色必须是 #RRGGBB 格式")
        String color,
        @Schema(description = "分层展示排序，数值较小者靠前") @Min(0) @Max(9999) int sortOrder,
        @Schema(description = "新建模型时建议使用的可编辑编码前缀；空白按未配置处理，非空时必须小写并以下划线结尾。它不是强制约束，不会自动添加到模型编码或改写已有模型。")
        @Size(max = 32)
        @Pattern(
                regexp = "[a-z][a-z0-9_]{0,30}_",
                message = "模型编码前缀必须以小写字母开头、以下划线结尾，且只能包含小写字母、数字和下划线"
        )
        String modelCodePrefix,
        @Schema(description = "输入分层规划策略；省略按 UNRESTRICTED 保存。当前只供展示和建模规划，不阻止任务保存、发布或运行。") ModelWarehouseLayerInputPolicy inputLayerPolicy,
        @Schema(description = "ALLOW_LIST 策略允许的输入分层 UUID，不能为 null 或重复；创建时只能选择当前启用分层，可包含刚创建的自身。UNRESTRICTED 时必须为空或省略。") List<UUID> allowedInputLayerIds
) {
}
