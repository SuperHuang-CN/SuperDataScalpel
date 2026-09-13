package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;

@Schema(description = "手工创建数据模型草稿。physicalTableMode 省略时创建没有初始字段的 MANAGED 模型；EXTERNAL 会立即读取指定 JDBC 物理表并导入全部可支持字段，但不会修改该表。")
public record CreateDataModelRequest(
        @Schema(description = "全局唯一模型编码，保存时去除首尾空白并转为小写，创建后不可修改。", example = "customer_master")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @Schema(description = "模型显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属 MODEL 范围目录 UUID；为空表示未分类") UUID directoryId,
        @Schema(description = "数仓分层 UUID；为空表示未分层，停用分层不能新分配") UUID warehouseLayerId,
        @Schema(description = "绑定的 JDBC 数据源 UUID；MANAGED 要求具备 STORAGE 用途，EXTERNAL 可使用普通 JDBC 数据源并在创建时读取表结构。") @NotNull UUID storageDataSourceId,
        @Schema(description = "MANAGED 的目标表名或 EXTERNAL 的已有表名；Catalog 与 Schema 由数据源连接和方言解析。保存时规范化为小写，EXTERNAL 输入本身必须是小写规范标识符。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Schema(description = "物理表模式：MANAGED 由平台受控创建；EXTERNAL 只绑定、读取并校验已有普通表。省略使用 MANAGED。") PhysicalTableMode physicalTableMode,
        @Schema(description = "仅 MANAGED 单机 ClickHouse 使用的 MergeTree ORDER BY 字段编码，保存时转为小写并去重，最多 16 个；空列表使用 tuple()。手工创建 MANAGED 模型时尚无字段，需在后续保存字段时确保这些编码存在。")
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Schema(description = "模型业务含义、粒度、更新口径或使用说明") @Size(max = 1000) String description
) {
}
