package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "将校对后的结构一次保存为 MANAGED + DRAFT 模型；先在事务外实时确认目标表不存在，再在管理库事务中保存模型和全部字段。不绑定来源、不导入数据、不执行建表 DDL。")
public record CreateManagedDraftRequest(
        @Schema(description = "全局唯一模型编码；保存时去除首尾空白并转为小写，创建后不可修改。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "模型显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属 MODEL 范围目录 UUID；为空表示未分类") UUID directoryId,
        @Schema(description = "启用的数仓分层 UUID；为空表示未分层。") UUID warehouseLayerId,
        @Schema(description = "已启用、具有 STORAGE 用途且支持受管建表的目标 JDBC 数据源 UUID；TDengine 当前不能作为受管目标。") @NotNull UUID storageDataSourceId,
        @Schema(description = "目标数据源默认 Catalog/Schema 中尚不存在、也未被其他模型占用的物理表名；保存时转为小写，创建草稿不会建立该表。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Schema(description = "仅 ClickHouse MANAGED 模型使用的 MergeTree ORDER BY 字段编码，最多 16 个；保存时转小写，不能重复、引用不存在字段或 GEOMETRY 字段，其他目标必须为空。")
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Schema(description = "模型业务含义、粒度、更新口径或使用说明") @Size(max = 1000) String description,
        @Schema(description = "由用户校对的完整字段列表，1 到 500 项；新建时所有字段 id 必须为空，字段编码和类型必须能由目标方言安全表达。") @NotEmpty @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {
}
