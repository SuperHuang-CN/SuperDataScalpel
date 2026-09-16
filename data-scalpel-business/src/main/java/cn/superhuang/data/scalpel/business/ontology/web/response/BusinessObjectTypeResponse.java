package cn.superhuang.data.scalpel.business.ontology.web.response;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "业务对象类型基础资料、当前定义和诊断。")
public record BusinessObjectTypeResponse(
        @Schema(description = "对象类型 UUID。") UUID id,
        @Schema(description = "稳定技术编码。") String code,
        @Schema(description = "显示名称。") String name,
        @Schema(description = "业务建模目录 UUID；根目录时为空。") UUID directoryId,
        @Schema(description = "业务负责人或责任部门。") String ownerName,
        @Schema(description = "对象业务含义和范围。") String summary,
        @Schema(description = "是否启用。停用后不能预览或被新关系引用。") boolean enabled,
        @Schema(description = "主来源模型名称；未配置或调用者缺少 model.view 权限时为空。") String mainSourceModelName,
        @Schema(description = "当前属性数量。") int propertyCount,
        @Schema(description = "包含出向和入向关系在内的关系数量。") int relationCount,
        @Schema(description = "当前直接生效的完整定义。") BusinessObjectTypeDefinition definition,
        @Schema(description = "当前定义诊断。") BusinessObjectTypeValidationResponse health,
        @Schema(description = "创建时间。") Instant createdAt,
        @Schema(description = "最后保存时间。") Instant updatedAt
) {
}
