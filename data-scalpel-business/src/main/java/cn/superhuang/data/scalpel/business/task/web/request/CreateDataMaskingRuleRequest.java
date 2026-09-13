package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建可供 Canvas MASK_FIELDS 节点引用的全局脱敏规则")
public record CreateDataMaskingRuleRequest(
        @Schema(description = "全局唯一规则编码，只允许小写字母、数字和下划线")
        @NotBlank
        @Size(max = 64)
        @Pattern(
                regexp = "[a-z0-9_]+",
                message = "规则编码只能包含小写字母、数字和下划线"
        )
        String code,
        @Schema(description = "脱敏规则显示名称，去除首尾空白后不能为空且最长 100 个字符。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "规则适用数据、效果和使用限制说明，去除首尾空白后最长 1000 个字符；为空或全空白时不保存。不得填写真实敏感值。") @Size(max = 1000) String description,
        @Schema(description = "完整强类型脱敏策略及参数。服务端拒绝与 strategy 无关的多余参数，并填充允许的默认掩码字符或位置后保存规范化定义。") @NotNull MaskingRuleDefinition definition
) {
}
