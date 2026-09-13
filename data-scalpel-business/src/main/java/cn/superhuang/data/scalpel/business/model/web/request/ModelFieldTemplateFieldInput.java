package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "常用字段模板中的一个字段快照；复制到模型后不再与模板保持关联")
public record ModelFieldTemplateFieldInput(
        @Schema(description = "已有模板字段 UUID；更新模板时用于保留稳定项，新字段为空，创建模板时必须为空。") UUID id,
        @Schema(description = "模板内唯一字段编码；保存时去除首尾空白并转为小写。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "字段显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "数据库无关的平台数据类型") @NotNull PlatformDataType fieldType,
        @Schema(description = "仅 STRING 保存的可选最大字符数，最小 1；其他标量类型提交时忽略并清空，GEOMETRY 明确拒绝。") @Min(1) Integer length,
        @Schema(description = "DECIMAL 必填的总有效位数，范围 1 到 38；其他标量类型提交时忽略并清空，GEOMETRY 明确拒绝。") @Min(1) @Max(38) Integer precision,
        @Schema(description = "DECIMAL 小数位数，范围 0 到 precision，省略按 0 保存；其他标量类型提交时忽略并清空，GEOMETRY 明确拒绝。") @Min(0) @Max(38) Integer scale,
        @Schema(description = "Geometry 定义；标量字段为空") @Valid GeometryTypeDefinition geometry,
        @Schema(description = "复制到模型后的字段是否允许 NULL") @NotNull Boolean nullable,
        @Schema(description = "复制到模型后的字段是否属于主键") @NotNull Boolean primaryKey,
        @Schema(description = "模板内字段排序，范围 0 到 9999；当前不要求连续或唯一，相同值再按字段编码排序。") @Min(0) @Max(9999) int sortOrder,
        @Schema(description = "字段业务含义、单位、口径或值域说明") @Size(max = 500) String description,
        @Schema(description = "可选码表 UUID；新绑定要求码表当前启用、值类型兼容且所有码值都能由字段类型表达。更新时可保留已停用的原码表绑定。模板只保存 UUID，调用方复制到模型时仍需通过模型接口重新校验。") UUID standardDictionaryId
) {
}
