package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "全局数仓分层、建模建议规则及引用统计")
public record ModelWarehouseLayerResponse(
        @Schema(description = "分层 UUID") UUID id,
        @Schema(description = "全局唯一的大写分层编码") String code,
        @Schema(description = "分层显示名称") String name,
        @Schema(description = "分层定位、数据粒度或建模职责说明") String description,
        @Schema(description = "界面展示颜色，格式为 #RRGGBB") String color,
        @Schema(description = "分层展示排序") int sortOrder,
        @Schema(description = "是否允许新模型分配到该分层；停用不清除已有模型引用") boolean enabled,
        @Schema(description = "新建模型编码的建议前缀；不会强制校验或改写已有模型") String modelCodePrefix,
        @Schema(description = "输入分层规划策略；当前不参与任务保存、发布或执行校验。") ModelWarehouseLayerInputPolicy inputLayerPolicy,
        @Schema(description = "ALLOW_LIST 策略保存的输入分层摘要，按 sortOrder、code、id 排列，可能包含后来被停用的分层；UNRESTRICTED 时为空列表。") List<ModelWarehouseLayerSummaryResponse> allowedInputLayers,
        @Schema(description = "当前引用该分层的模型数量") long referencedModelCount,
        @Schema(description = "其他目标分层把该分层列为允许输入的数量；不计算自身对自身的关系。") long referencedAsInputByLayerCount,
        @Schema(description = "是否既无模型引用、也未被其他分层规则引用，可以删除；自身保存的允许输入关系不影响该值。") boolean deletable,
        @Schema(description = "分层创建时间") Instant createdAt,
        @Schema(description = "分层最后更新时间") Instant updatedAt
) {
    public static ModelWarehouseLayerResponse from(
            ModelWarehouseLayer layer,
            List<ModelWarehouseLayerSummaryResponse> allowedInputLayers,
            long referencedModelCount,
            long referencedAsInputByLayerCount
    ) {
        return new ModelWarehouseLayerResponse(
                layer.getId(),
                layer.getCode(),
                layer.getName(),
                layer.getDescription(),
                layer.getColor(),
                layer.getSortOrder(),
                layer.isEnabled(),
                layer.getModelCodePrefix(),
                layer.getInputLayerPolicy(),
                List.copyOf(allowedInputLayers),
                referencedModelCount,
                referencedAsInputByLayerCount,
                referencedModelCount == 0 && referencedAsInputByLayerCount == 0,
                layer.getCreatedAt(),
                layer.getUpdatedAt()
        );
    }
}
