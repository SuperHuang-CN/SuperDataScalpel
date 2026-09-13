package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "按版本原子修改常用字段模板元数据并整体替换字段快照；遗漏的原字段会删除，带合法 id 的字段保留身份，id 为空则新增。不会传播到已复制的模型字段。")
public record UpdateModelFieldTemplateRequest(
        @Schema(description = "客户端读取到的模板版本；任何成功的内容更新或实际启停变化都会递增版本，与当前版本不一致时返回 409。") @Min(1) int expectedVersion,
        @Schema(description = "全局唯一模板编码；保存时去除首尾空白并转为大写。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "模板显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "自由填写的扁平分类名称；为空表示未分类") @Size(max = 100) String category,
        @Schema(description = "模板适用场景或复制说明") @Size(max = 500) String description,
        @Schema(description = "模板列表排序，数值较小者靠前") @Min(0) @Max(9999) int sortOrder,
        @Schema(description = "修改后的完整模板字段快照，1 到 100 个；每个非空 id 必须属于当前模板且不能重复，遗漏的原字段会删除。") @NotNull @Size(min = 1, max = 100) List<@Valid ModelFieldTemplateFieldInput> fields
) {
}
