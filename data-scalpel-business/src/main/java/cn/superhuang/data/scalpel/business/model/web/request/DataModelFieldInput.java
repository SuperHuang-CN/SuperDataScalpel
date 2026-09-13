package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "模型字段定义；平台类型及参数共同构成数据库无关的结构契约。整体更新或物理变更计划中，已有字段通过 id 保留身份，id 为空表示新增字段。")
public record DataModelFieldInput(
        @Schema(description = "已有字段 UUID；整体更新时用于保留稳定字段身份，新字段为空") UUID id,
        @Schema(description = "模型内唯一字段编码，也是受控物理列名；保存时去除首尾空白并转为小写。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "字段显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "数据库无关的平台数据类型") @NotNull PlatformDataType fieldType,
        @Schema(description = "仅 STRING 使用的可选最大长度，最小 1；为空表示无显式长度上限。其他标量类型提交该值时当前服务端会忽略并清空，GEOMETRY 明确拒绝。") @Min(1) Integer length,
        @Schema(description = "DECIMAL 必填的总精度，范围 1 到 38；其他标量类型提交时忽略，GEOMETRY 明确拒绝。") @Min(1) @Max(38) Integer precision,
        @Schema(description = "DECIMAL 小数位数，范围 0 到 precision，省略按 0 保存；其他标量类型提交时忽略，GEOMETRY 明确拒绝。") @Min(0) @Max(38) Integer scale,
        @Schema(description = "GEOMETRY 的子类型、EPSG CRS 和坐标维度；标量字段必须为空") @Valid GeometryTypeDefinition geometry,
        @Schema(description = "物理列是否允许 NULL；主键字段必须为 false") @NotNull Boolean nullable,
        @Schema(description = "字段是否属于模型主键；复合主键按 sortOrder 确定顺序") @NotNull Boolean primaryKey,
        @Schema(description = "字段展示和受管建表顺序，从 0 开始；当前不要求连续或全局唯一，相同值再按字段编码排序。") @Min(0) int sortOrder,
        @Schema(description = "字段业务含义、单位、口径或值域说明") @Size(max = 500) String description,
        @Schema(description = "可选树形码表 UUID；只增加业务元数据，不改变 DDL 或物理结构指纹") UUID standardDictionaryId
) {
}
