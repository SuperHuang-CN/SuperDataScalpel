package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "元数据整批导入后创建的一张 MANAGED + DRAFT 模型摘要。")
public record ImportedModelMetadataResponse(
        @Schema(description = "新建模型 UUID") UUID id,
        @Schema(description = "本次元数据导入创建的模型稳定编码") String code,
        @Schema(description = "模型显示名称") String name,
        @Schema(description = "匹配到的 MODEL 目录 UUID；未分类时为空") UUID directoryId
) {
}
