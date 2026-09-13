package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingStrategy;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "可复制到 Canvas MASK_FIELDS 节点的全局数据脱敏规则。响应包含完整 definition；任务保存时复制它，之后不自动跟随全局规则变化。")

public record DataMaskingRuleResponse(
        @Schema(description = "脱敏规则 UUID；Canvas GLOBAL 配置把它保存为来源身份快照，运行时不使用该 UUID 回查规则。")
        UUID id,
        @Schema(description = "脱敏规则全局唯一稳定编码，创建后不可修改；Canvas 仅保存其展示快照。")
        String code,
        @Schema(description = "脱敏规则显示名称。")
        String name,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "脱敏策略类型，决定 definition 的具体结构和处理语义。")
        MaskingStrategy strategy,
        @Schema(description = "与 strategy 匹配且已经规范化的完整脱敏参数定义；复制到 Canvas 后成为唯一执行配置。")
        MaskingRuleDefinition definition,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "名称、说明或 definition 最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
