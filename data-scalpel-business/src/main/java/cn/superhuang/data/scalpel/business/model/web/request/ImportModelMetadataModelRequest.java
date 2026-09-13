package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Excel 预览后提交创建的一张 MANAGED + DRAFT 模型。服务端不保存预览会话或校验 key，而是按当前系统状态重新校验本对象。")
public record ImportModelMetadataModelRequest(
        @Schema(description = "全局唯一模型编码；保存时去除首尾空白并转为小写，不自动添加数仓分层前缀。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "模型显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "已有 MODEL 目录完整路径，以 / 分隔并逐级忽略大小写匹配；为空表示未分类。导入不会创建目录，提交到落库之间目录解析结果变化会使整批失败。") @Size(max = 1000) String directoryPath,
        @Schema(description = "校对后选择的启用数仓分层 UUID；为空表示未分层。服务端按 UUID 重新校验，不使用 Excel 中的分层名称。") UUID warehouseLayerId,
        @Schema(description = "目标数据源默认 Catalog/Schema 中尚不存在、也未被其他模型占用的物理表名；保存时转为小写。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Schema(description = "仅目标为 ClickHouse 时使用的 MergeTree ORDER BY 字段编码；保存时转小写，最多 16 个且不能重复、引用不存在字段或 GEOMETRY 字段。其他目标必须为空；预览会自动清空 Excel 中不适用的值并给出 warning。")
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Schema(description = "模型业务含义、粒度、更新口径或使用说明") @Size(max = 1000) String description,
        @Schema(description = "校对后的完整字段定义，1 到 500 个；所有字段 id 必须为空，字段编码、码表和目标方言类型映射会重新校验。") @NotEmpty @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {

    public ImportModelMetadataModelRequest {
        clickHouseOrderByColumns = clickHouseOrderByColumns == null
                ? List.of() : List.copyOf(clickHouseOrderByColumns);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
