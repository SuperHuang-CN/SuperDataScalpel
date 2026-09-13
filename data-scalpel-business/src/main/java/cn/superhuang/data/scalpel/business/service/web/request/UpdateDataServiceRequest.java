package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "整体修改草稿或已停用数据服务的基础资料、目标引擎、路径和可选完整定义；已有部署记录必须为 REMOVED。")

public record UpdateDataServiceRequest(
        @Schema(description = "数据服务显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "承载该服务部署和查询的兼容服务引擎 UUID。")
        @NotNull UUID engineId,
        @Schema(description = "STANDARD_TABLE、SQL_QUERY、SCRIPT_API 必填的 Engine 上下文路径：规范化为 /open-api/v1/ 下的小写静态路径，不得以 / 结尾或包含 //。SPATIAL_SERVICE 必须为空，非空值会被拒绝。")
        @Size(max = 255) String contextPath,
        @Schema(description = "服务类型必须与创建时类型一致。四个定义都为空时保留已有定义；提交时必须只提供与该类型匹配的一种完整定义，并按目标 engineId 重新校验来源登记。")
        @NotNull DataServiceType type,
        @Schema(description = "type=STANDARD_TABLE 时可提交的完整模型绑定；提供时整体替换现有标准服务定义。")
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Schema(description = "type=SQL_QUERY 时可提交的完整只读 SQL 服务定义；提供时整体替换现有 SQL 定义。")
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Schema(description = "type=SCRIPT_API 时可提交的完整可信 Groovy 服务定义；提供时整体替换现有脚本定义，脚本允许读写数据。")
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Schema(description = "type=SPATIAL_SERVICE 时的完整空间模型绑定；整体替换现有空间定义。")
        @Valid SpatialDataServiceDefinitionRequest spatialDefinition,
        @Schema(description = "数据服务用途、消费者或使用限制说明；传空值表示清除。")
        @Size(max = 1000) String description
) {
    public UpdateDataServiceRequest(
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
                name, directoryId, engineId, contextPath, type,
                standardDefinition, sqlDefinition, scriptDefinition, null, description
        );
    }
}
