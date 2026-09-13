package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "数据服务列表项，汇总定义、业务启停、引擎部署及网关发布状态。")

public record DataServiceSummaryResponse(
        @Schema(description = "数据服务 UUID，用于详情、更新、部署和网关发布接口。")
        UUID id,
        @Schema(description = "数据服务唯一稳定编码；创建后用于识别服务，不随名称修改。")
        String code,
        @Schema(description = "数据服务显示名称。")
        String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "服务类型：标准表查询、参数化 SQL、受限脚本 API 或空间服务。")
        DataServiceType type,
        @Schema(description = "当前服务类型所需的定义是否已经保存；false 时不能启用。SQL 测试和脚本草稿调试可使用未保存定义，仍由对应接口校验。")
        boolean definitionConfigured,
        @Schema(description = "当前保存定义的递增版本号；尚未配置定义时为空。")
        Integer definitionVersion,
        @Schema(description = "定义中保存的主来源 UUID；STANDARD_TABLE、SPATIAL_SERVICE 指向模型，SQL_QUERY、SCRIPT_API 指向数据源。尚未配置定义时为空；来源记录已删除时仍保留 UUID。")
        UUID sourceId,
        @Schema(description = "主来源模型或数据源的当前名称；尚未配置定义时为空，来源记录已删除时为“已删除”。")
        String sourceName,
        @Schema(description = "承载该数据服务的服务引擎 UUID；尚未选择引擎时为空。")
        UUID engineId,
        @Schema(description = "数据服务在运行引擎中的实际上下文路径；SPATIAL_SERVICE 由 GeoServer 图层路由决定，因此为空。其他类型在保存时即有值。")
        String contextPath,
        @Schema(description = "服务业务状态。ENABLED 仍需结合 deploymentStatus=DEPLOYED 和网关绑定判断实际入口；停用远端失败时服务会保持 ENABLED。")
        DataServiceStatus status,
        @Schema(description = "运行部署修订号，初始为 0；开始部署新定义快照时递增，同一失败或中断快照重试会复用原修订。用于核对 Engine 和网关应用快照，不是基础资料更新的乐观锁版本。")
        long revision,
        @Schema(description = "服务引擎部署状态；从未尝试启用时为空。PENDING、DEPLOYED、FAILED、REMOVING、REMOVED 的含义见枚举说明，与业务启停状态分开。")
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
