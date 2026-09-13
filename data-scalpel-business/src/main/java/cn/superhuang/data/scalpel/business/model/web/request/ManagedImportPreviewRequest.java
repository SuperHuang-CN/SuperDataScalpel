package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "将一个 JDBC 来源普通表的结构经平台类型映射为目标数据存储上的 MANAGED 模型字段候选；会连接来源读取元数据，并为含 GEOMETRY 的完整候选调用目标方言规划建表以校验运行能力，但不读取业务行、不保存模型、不执行 DDL。")
public record ManagedImportPreviewRequest(
        @Schema(description = "已启用 JDBC 来源数据源 UUID；该预览不要求数据源声明 SOURCE 或 STORAGE 用途。") @NotNull UUID sourceDataSourceId,
        @Schema(description = "来源表的 Catalog、Schema 和表名") @NotNull @Valid ManagedImportTableIdentifierInput sourceTable,
        @Schema(description = "已启用、具有 STORAGE 用途且支持受管建表的目标 JDBC 数据源 UUID，用于验证平台类型到目标物理类型的安全映射；TDengine 当前不支持。") @NotNull UUID targetStorageDataSourceId
) {
}
