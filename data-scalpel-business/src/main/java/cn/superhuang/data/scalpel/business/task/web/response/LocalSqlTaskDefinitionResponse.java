package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Local SQL 任务当前保存定义及已解析模型和数据源摘要；DRAFT 或 DISABLED 均可保存，PUBLISHED 只读。")
public record LocalSqlTaskDefinitionResponse(
        @Schema(description = "任务 UUID") UUID taskId,
        @Schema(description = "是否已保存过 Local SQL 定义。") boolean configured,
        @Schema(description = "任务定义内容版本；未配置时为 0，首次保存为 1，SQL、输入顺序、输出、写入模式或超时实际变化时递增；重复保存相同内容不递增。") int version,
        @Schema(description = "已保存 SQL 查询文本；未配置时为空") String sql,
        @Schema(description = "允许 SQL 引用的输入模型，保留未使用声明并在校验中提示") List<TaskModelReferenceResponse> inputs,
        @Schema(description = "查询结果写入的输出模型；未配置时为空") TaskModelReferenceResponse output,
        @Schema(description = "输入与输出模型解析到的共同 JDBC 数据源；未配置或跨源无效时为空") TaskDataSourceReferenceResponse resolvedDataSource,
        @Schema(description = "APPEND 追加或 OVERWRITE 受控覆盖；未配置时返回 APPEND。") LocalSqlWriteMode writeMode,
        @Schema(description = "单次 SQL 查询和写入运行超时，单位秒；未配置时返回 300。") Integer timeoutSeconds,
        @Schema(description = "定义最后保存时间，ISO-8601 UTC 时间戳；未配置时为空。") Instant updatedAt
) {

    public static LocalSqlTaskDefinitionResponse unconfigured(UUID taskId) {
        return new LocalSqlTaskDefinitionResponse(
                taskId, false, 0, null, List.of(), null, null, LocalSqlWriteMode.APPEND, 300, null
        );
    }
}
