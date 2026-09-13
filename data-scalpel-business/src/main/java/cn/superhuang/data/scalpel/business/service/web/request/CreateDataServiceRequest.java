package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "创建标准表、SQL、脚本或空间数据服务草稿，并可同时保存匹配类型的初始定义；即使提供完整定义也不会部署或启用。")

public record CreateDataServiceRequest(
        @Schema(description = "数据服务稳定技术编码，创建时去除首尾空白并转为小写，全局唯一，创建后不可修改。")
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String code,
        @Schema(description = "数据服务显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "承载该服务部署和查询的兼容服务引擎 UUID。")
        @NotNull UUID engineId,
        @Schema(description = "STANDARD_TABLE、SQL_QUERY、SCRIPT_API 必填的 Engine 上下文路径：去除首尾空白并转小写，必须是 /open-api/v1/ 下的静态路径，不得以 / 结尾或包含 //。SPATIAL_SERVICE 必须省略或留空，非空值会被拒绝。")
        @Size(max = 255) String contextPath,
        @Schema(description = "服务类型：STANDARD_TABLE、SQL_QUERY、SCRIPT_API 或 SPATIAL_SERVICE，创建后不可修改。可不提交任何定义以创建可恢复草稿；提交时只能提供与该类型匹配的一种定义。")
        @NotNull DataServiceType type,
        @Schema(description = "type=STANDARD_TABLE 时可选的初始定义；模型必须已发布、拥有字段，其 STORAGE JDBC 数据源必须启用并已在目标引擎同步 READY。公开字段和查询能力在启用时重新从模型生成。")
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Schema(description = "type=SQL_QUERY 时可选的初始定义；声明已在目标引擎同步 READY 的执行数据源、有序关联模型、只读单语句参数化 SQL 和参数。保存只做本地 SQL 校验，不执行查询。")
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Schema(description = "type=SCRIPT_API 时可选的初始定义；声明已在目标引擎同步 READY 的非 TDengine JDBC 数据源、可信 Groovy 脚本和请求示例。脚本允许读写业务数据，保存时不执行。")
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Schema(description = "type=SPATIAL_SERVICE 时可选的初始定义；绑定已发布、具有唯一非 Geometry 主键和唯一 XY/EPSG Geometry 字段的 PostGIS 模型，其 STORAGE 数据源须在目标 GeoServer 同步 READY。")
        @Valid SpatialDataServiceDefinitionRequest spatialDefinition,
        @Schema(description = "数据服务用途、消费者或使用限制说明；未填写时为空。")
        @Size(max = 1000) String description
) {
    public CreateDataServiceRequest(
            String code,
            String name,
            UUID directoryId,
            UUID engineId,
            String contextPath,
            DataServiceType type,
            StandardDataServiceDefinitionRequest standardDefinition,
            SqlDataServiceDefinitionRequest sqlDefinition,
            ScriptDataServiceDefinitionRequest scriptDefinition,
            String description
    ) {
        this(
                code, name, directoryId, engineId, contextPath, type,
                standardDefinition, sqlDefinition, scriptDefinition, null, description
        );
    }
}
