package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "本体总览中的一个业务对象类型节点摘要。配置诊断和来源可用性须按需检查。")
public record BusinessObjectTypeGraphNodeResponse(
        @Schema(description = "对象类型 UUID。") UUID id,
        @Schema(description = "对象类型显示名称。") String name,
        @Schema(description = "对象类型稳定技术编码。") String code,
        @Schema(description = "业务建模目录 UUID；未分类时为空。") UUID directoryId,
        @Schema(description = "是否启用。停用不等同于配置异常。") boolean enabled,
        @Schema(description = "主来源模型 UUID；尚未配置时为空。") UUID mainSourceModelId,
        @Schema(description = "主来源模型名称；引用失效或调用者缺少 model.view 权限时为空。") String mainSourceModelName,
        @Schema(description = "主来源模型编码；引用失效或调用者缺少 model.view 权限时为空。") String mainSourceModelCode,
        @Schema(description = "当前业务属性数量。") int propertyCount,
        @Schema(description = "当前属性分组数量。") int groupCount,
        @Schema(description = "当前补充来源数量。") int supplementCount,
        @Schema(description = "当前登记的查询、计算和 Action 能力数量；这些能力不代表已接入执行。") int capabilityCount,
        @Schema(description = "对象类型最后保存时间。") Instant updatedAt
) {
}
