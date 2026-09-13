package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "数据服务基础信息、当前类型定义、引擎部署状态和网关绑定的完整详情。")

public record DataServiceDetailResponse(
        @Schema(description = "数据服务 UUID。")
        UUID id,
        @Schema(description = "数据服务稳定技术编码，创建后不可修改。")
        String code,
        @Schema(description = "数据服务显示名称。")
        String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "服务类型，决定 standardDefinition、sqlDefinition、scriptDefinition 或 spatialDefinition 中哪一个生效。")
        DataServiceType type,
        @Schema(description = "当前服务类型所需的定义是否已经保存；false 时对应定义字段为空，不能启用。")
        boolean definitionConfigured,
        @Schema(description = "当前保存定义的版本号，从 1 开始，仅在定义内容实际变化时递增；尚未配置定义时为空。")
        Integer definitionVersion,
        @Schema(description = "STANDARD_TABLE 类型的当前完整定义；尚未配置或其他服务类型时为空。")
        StandardDataServiceDefinitionResponse standardDefinition,
        @Schema(description = "SQL_QUERY 类型的当前完整定义；尚未配置或其他服务类型时为空。")
        SqlDataServiceDefinitionResponse sqlDefinition,
        @Schema(description = "SCRIPT_API 类型的当前完整定义；尚未配置或其他服务类型时为空。")
        ScriptDataServiceDefinitionResponse scriptDefinition,
        @Schema(description = "SPATIAL_SERVICE 类型的当前完整定义；尚未配置或其他服务类型时为空。")
        SpatialDataServiceDefinitionResponse spatialDefinition,
        @Schema(description = "承载该服务部署和查询的服务引擎 UUID；尚未选择时为空。")
        UUID engineId,
        @Schema(description = "数据服务在运行引擎中的实际上下文路径；SPATIAL_SERVICE 由 GeoServer 图层路由决定，因此为空。")
        String contextPath,
        @Schema(description = "服务业务状态。ENABLED 表示期望运行，但停用远端失败时仍保持 ENABLED；实际可调用性还取决于 deploymentStatus 和网关绑定。")
        DataServiceStatus status,
        @Schema(description = "运行部署修订号，初始为 0；开始部署新定义快照时递增，同一失败或中断快照重试会复用原修订。用于区分 Engine 和网关应用快照，不是基础资料更新的乐观锁版本。")
        long revision,
        @Schema(description = "服务引擎部署状态；从未尝试启用时为空。PENDING、DEPLOYED、FAILED、REMOVING、REMOVED 的含义见枚举说明，不等同于业务 ENABLED/DISABLED。")
        DataServiceDeploymentStatus deploymentStatus,
        @Schema(description = "最近一次 Engine 部署或移除失败的安全摘要；当前部署记录没有失败时为空。")
        String deploymentError,
        @Schema(description = "最近一次成功部署到 Engine 的时间，ISO-8601 UTC 时间戳；从未成功部署时为空。后续移除或失败不会清除此历史时间。")
        Instant deployedAt,
        @Schema(description = "按 provider 排序的网关绑定列表；没有时为空列表。只有 publicationStatus=PUBLISHED 且 publishedRevision 等于当前 revision 才表示当前修订已确认发布。")
        List<GatewayServiceBindingResponse> gatewayBindings,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
