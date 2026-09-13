package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "引用当前码表的一个真实模型字段")
public record StandardDictionaryFieldReferenceResponse(
        @Schema(description = "直接包含该绑定字段的纳管模型 UUID") UUID modelId,
        @Schema(description = "模型全局稳定技术编码") String modelCode,
        @Schema(description = "模型显示名称") String modelName,
        @Schema(description = "模型当前生命周期：DRAFT 草稿、PUBLISHED 已发布、DISABLED 已停用") DataModelStatus modelStatus,
        @Schema(description = "模型物理表模式：MANAGED 由平台管理，EXTERNAL 绑定外部已有表") PhysicalTableMode physicalTableMode,
        @Schema(description = "包含该绑定关系的当前模型 Schema 版本；模型字段元数据变化时递增") int schemaVersion,
        @Schema(description = "直接绑定该码表的模型字段 UUID") UUID fieldId,
        @Schema(description = "模型内稳定唯一的字段编码，也是业务物理表列名") String fieldCode,
        @Schema(description = "字段显示名称") String fieldName,
        @Schema(description = "字段平台数据类型；建立或保存绑定时已校验能够无损表达该码表的全部现有节点 code") PlatformDataType fieldType
) {
}
