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

@Schema(description = "在一个管理库事务中创建初始启用、版本为 1 的全局常用字段模板及完整字段快照。当前没有服务端“应用模板”命令，调用方读取字段后自行复制进模型请求。")
public record CreateModelFieldTemplateRequest(
        @Schema(description = "全局唯一模板编码；保存时去除首尾空白并转为大写。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "模板显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "自由填写的扁平分类名称；为空表示未分类") @Size(max = 100) String category,
        @Schema(description = "模板适用场景或复制说明") @Size(max = 500) String description,
        @Schema(description = "模板列表排序，数值较小者靠前") @Min(0) @Max(9999) int sortOrder,
        @Schema(description = "模板字段完整快照，1 到 100 个；创建时所有字段 id 必须为空。") @NotNull @Size(min = 1, max = 100) List<@Valid ModelFieldTemplateFieldInput> fields
) {
}
