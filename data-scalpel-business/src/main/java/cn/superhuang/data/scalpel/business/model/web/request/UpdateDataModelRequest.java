package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;

@Schema(description = "修改模型基础资料。只有 DRAFT 可改变数据源、物理位置、模式或 ClickHouse 排序键；PUBLISHED 和 DISABLED 只能修改名称、目录、分层和说明，并必须原样提交现有物理配置。")
public record UpdateDataModelRequest(
        @Schema(description = "模型显示名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属 MODEL 范围目录 UUID；为空表示未分类") UUID directoryId,
        @Schema(description = "数仓分层 UUID；为空表示未分层，调整分层不改变 Schema 版本") UUID warehouseLayerId,
        @Schema(description = "绑定的 JDBC 数据源 UUID；只有 DRAFT 可切换，切换时重新解析 Catalog 和 Schema；其他状态必须提交当前值。") @NotNull UUID storageDataSourceId,
        @Schema(description = "目标或已存在的物理表名，保存时转为小写；只有 DRAFT 可修改，PUBLISHED 或 DISABLED 必须提交当前值。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Schema(description = "物理表模式：MANAGED 由平台受控创建，EXTERNAL 只绑定已有表。省略按 MANAGED 处理，并不表示保留现值；修改 EXTERNAL 模型时应显式提交 EXTERNAL。") PhysicalTableMode physicalTableMode,
        @Schema(description = "仅 MANAGED 单机 ClickHouse 使用的 MergeTree ORDER BY 字段编码，最多 16 个；省略保留当前列表，显式空数组清空。只有 DRAFT 且物理表仍不存在时可直接改变现有排序键。")
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Schema(description = "模型业务含义、粒度、更新口径或使用说明") @Size(max = 1000) String description
) {
}
