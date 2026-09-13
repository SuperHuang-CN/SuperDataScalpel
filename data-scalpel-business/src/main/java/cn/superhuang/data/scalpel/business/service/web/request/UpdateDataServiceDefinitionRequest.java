package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** Replaces the complete design-time definition for the service's immutable type. */
@Schema(description = "按数据服务创建时确定的类型整体替换当前设计期定义；必须恰好提供一种匹配定义，不能用空请求清除定义。服务需非 ENABLED 且部署状态已确认 REMOVED。")
public record UpdateDataServiceDefinitionRequest(
        @Schema(description = "STANDARD 类型的模型、查询字段、筛选、分页和排序定义；其他服务类型必须为空。")
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Schema(description = "SQL 类型的只读 SQL、参数和响应字段定义；其他服务类型必须为空。")
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Schema(description = "SCRIPT 类型的数据源、受控 Groovy 脚本和请求示例定义；其他服务类型必须为空。")
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Schema(description = "SPATIAL_SERVICE 类型的发布模型定义；其他服务类型必须为空。")
        @Valid SpatialDataServiceDefinitionRequest spatialDefinition
) {
    public UpdateDataServiceDefinitionRequest(
            StandardDataServiceDefinitionRequest standardDefinition,
            SqlDataServiceDefinitionRequest sqlDefinition,
            ScriptDataServiceDefinitionRequest scriptDefinition
    ) {
        this(standardDefinition, sqlDefinition, scriptDefinition, null);
    }
}
