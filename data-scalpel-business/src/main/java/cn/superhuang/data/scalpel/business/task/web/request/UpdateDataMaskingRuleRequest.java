package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "整体修改全局脱敏规则名称、说明和规范化定义；规则编码保持不变，已经复制到 Canvas 的内嵌定义不自动同步。")
public record UpdateDataMaskingRuleRequest(
        @Schema(description = "脱敏规则显示名称，去除首尾空白后不能为空且最长 100 个字符。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "规则适用数据、效果和使用限制说明，去除首尾空白后最长 1000 个字符；为空或全空白时清除。不得填写真实敏感值。") @Size(max = 1000) String description,
        @Schema(description = "完整替换后的强类型脱敏定义。服务端规范化后保存；任何已保存 Canvas，无论任务生命周期状态，均继续使用其内嵌快照，不会自动读取此新值。") @NotNull MaskingRuleDefinition definition
) {
}
