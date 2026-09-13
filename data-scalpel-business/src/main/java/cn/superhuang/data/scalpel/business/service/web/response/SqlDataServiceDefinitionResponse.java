package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;

import java.util.List;
import java.util.UUID;

@Schema(description = "参数化 SQL 数据服务的当前定义，包含允许引用的数据源、模型和请求参数。")

public record SqlDataServiceDefinitionResponse(
        @Schema(description = "执行 SQL 的数据源 UUID。")
        UUID dataSourceId,
        @Schema(description = "按保存顺序排列的关联模型 UUID 列表；用于来源说明、血缘、编辑辅助和删除保护，不限制 SQL 实际访问的表，也不表示查询结果顺序。")
        List<UUID> modelIds,
        @Schema(description = "只允许一条 SELECT 或 WITH ... SELECT 的参数化 SQL；可引用 parameters 中声明的命名参数，平台不解析或限制 SQL 实际访问的表。")
        String sqlText,
        @Schema(description = "SQL 占位符可使用的请求参数定义列表。")
        List<SqlServiceParameterDefinition> parameters,
        @Schema(description = "该 SQL 定义的版本号，初始为 1；数据源、规范化后的 SQL、参数定义或关联模型顺序实际变化时递增，重复保存相同定义不变。")
        int version
) {

    public SqlDataServiceDefinitionResponse {
        modelIds = List.copyOf(modelIds);
        parameters = List.copyOf(parameters);
    }
}
