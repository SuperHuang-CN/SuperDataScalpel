package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "整体保存 DRAFT 或 DISABLED Local SQL 任务定义；只执行本地 SQL 语法与模型关系校验，不连接 JDBC。发布、显式校验和运行时再检查物理表、结果列及外部可用性。")
public record UpdateLocalSqlTaskDefinitionRequest(
        @Schema(description = "本地 SQL 查询文本，最长 100000 个字符；保存时解析为受控单条只读 SELECT/CTE，拒绝多语句和任意 DDL/DML。发布时还会连接 JDBC 验证结果列并生成血缘。") @NotBlank @Size(max = 100_000) String sql,
        @Schema(description = "SQL 显式声明的输入模型 UUID，1 到 50 项且不能重复；保存时模型必须存在、与输出模型使用同一数据源，发布时还要求模型均为 PUBLISHED。服务端不会根据 SQL 文本自动修改该列表。") @NotEmpty @Size(max = 50) List<@NotNull UUID> inputModelIds,
        @Schema(description = "查询结果写入的输出模型 UUID；不能同时出现在 inputModelIds 中，保存时必须与全部输入模型使用同一数据源，发布时要求模型为 PUBLISHED 且物理结构可用。") @NotNull UUID outputModelId,
        @Schema(description = "写入模式：APPEND 追加；OVERWRITE 仅允许输出模型使用平台管理物理表。发布还会检查目标数据库方言能力，覆盖原子性由方言实现决定。") @NotNull LocalSqlWriteMode writeMode,
        @Schema(description = "JDBC 预检与单次正式 SQL 运行超时，单位秒，范围 1 到 3600。JDBC 驱动不支持查询超时设置时只能尽力执行。") @NotNull @Min(1) @Max(3600) Integer timeoutSeconds
) {
}
