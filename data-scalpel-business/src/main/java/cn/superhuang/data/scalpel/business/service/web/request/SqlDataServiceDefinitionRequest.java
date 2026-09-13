package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "SQL 数据服务的数据源、允许引用模型、参数化只读 SQL 和请求参数定义。")

public record SqlDataServiceDefinitionRequest(
        @Schema(description = "执行只读 SQL 的已启用 JDBC 数据源 UUID。")
        @NotNull UUID dataSourceId,
        @Schema(description = "有序关联模型 UUID 列表，至少一个且都必须使用 dataSourceId 作为存储数据源；模型可为草稿、已发布或已停用。该列表只用于来源说明、血缘、编辑辅助和删除保护，不限制 SQL 实际访问的表。")
        @NotEmpty List<@NotNull UUID> modelIds,
        @Schema(description = "只允许一条 SELECT 或 WITH ... SELECT 的参数化 SQL，最长 100000 字符。可使用已声明的命名参数；平台不解析或限制 SQL 实际访问的表。")
        @NotBlank @Size(max = 100_000) String sqlText,
        @Schema(description = "SQL 中允许引用的命名参数定义；名称必须唯一、全部被 SQL 使用，SQL 中出现的参数也必须全部声明，且不支持 BINARY 或 GEOMETRY 参数。")
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters
) {

    public SqlDataServiceDefinitionRequest {
        modelIds = modelIds == null ? List.of() : List.copyOf(modelIds);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
