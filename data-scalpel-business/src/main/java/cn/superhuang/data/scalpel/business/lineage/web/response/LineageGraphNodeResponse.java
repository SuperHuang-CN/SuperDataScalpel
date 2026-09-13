package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageExternalResourceType;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.util.UUID;
import java.util.List;

@Schema(description = "血缘图中的模型、物理表、外部资源、任务、字段或数据服务节点。")

public record LineageGraphNodeResponse(
        @Schema(description = "血缘图内的节点稳定键；供 rootNodeId 和边的 source/target 引用，不保证是数据库 UUID。")
        String id,
        @Schema(description = "节点代表的对象类型：模型、JDBC 表、外部资源、任务、字段或数据服务。")
        LineageGraphNodeKind kind,
        @Schema(description = "节点相对查询焦点的位置：CURRENT 为焦点本身，UPSTREAM 为其上游，DOWNSTREAM 为其下游。")
        LineageGraphNodeSide side,
        @Schema(description = "节点与查询焦点之间的最短血缘转换跳数；焦点为 0，每跨一条关系增加 1。")
        int depth,
        @Schema(description = "用于血缘图主标题展示的对象名称或字段名称。")
        String label,
        @Schema(description = "用于补充节点上下文的次级说明，例如资源类型、所属对象或业务编码；没有时为空。")
        String subtitle,
        @Schema(description = "节点对应或所属的纳管模型 UUID；仅模型及能解析到模型归属的字段节点适用。")
        UUID modelId,
        @Schema(description = "节点对应的纳管模型字段 UUID；仅 FIELD 节点且能够解析到当前模型字段时存在。")
        UUID modelFieldId,
        @Schema(description = "节点对应的任务 UUID；仅 TASK 节点适用。")
        UUID taskId,
        @Schema(description = "节点所引用数据源的 UUID；JDBC 表或外部资源能够解析到数据源时适用。")
        UUID dataSourceId,
        @Schema(description = "任务当前生命周期状态；仅 TASK 节点适用，不能据此判断某次运行是否成功。")
        TaskStatus taskStatus,
        @Schema(description = "生成当前血缘证据时使用的任务定义版本；仅 TASK 节点适用。")
        Integer definitionVersion,
        @Schema(description = "任务向目标资产写入数据的方式，例如追加、全量覆盖、更新插入、分区覆盖、快照同步或新建；仅写入侧节点适用。")
        LineageWriteMode writeMode,
        @Schema(description = "血缘证据是否已过期；true 表示任务定义或关联资源在证据生成后发生变化，关系可展示但不应视为当前完整事实。")
        boolean stale,
        @Schema(description = "外部资源的具体种类，例如 Kafka Topic、文件数据集表、HTTP API、空间服务资源、对象存储路径或 JDBC 查询结果；仅 EXTERNAL_RESOURCE 节点适用。")
        LineageExternalResourceType externalResourceType,
        @Schema(description = "节点对应的来源业务资源 UUID；外部资源能够映射到系统内登记对象时存在。")
        UUID resourceId,
        @Schema(description = "节点对应的数据服务 UUID；仅 DATA_SERVICE 节点适用。")
        UUID dataServiceId,
        @Schema(description = "数据服务提供能力的类型；仅 DATA_SERVICE 节点适用，并决定服务定义及调用方式。")
        DataServiceType dataServiceType,
        @Schema(description = "数据服务当前业务生命周期状态；仅 DATA_SERVICE 节点适用，不等同于底层服务引擎的实时部署状态。")
        DataServiceStatus dataServiceStatus,
        @Schema(description = "数据服务发布后使用的对外路由路径；仅已配置路由的数据服务节点存在。")
        String routePath,
        @Schema(description = "FIELD 节点所属的模型、外部资源或数据服务节点摘要；非字段节点为空。")
        LineageFieldOwnerResponse fieldOwner,
        @Schema(description = "节点是否是本次字段血缘查询选中的焦点根节点。")
        boolean focusRoot,
        @Schema(description = "与该节点相关的焦点字段稳定键；用于多字段查询时标明节点服务于哪些焦点字段。")
        List<String> focusFieldKeys
) {
    public LineageGraphNodeResponse {
        focusFieldKeys = focusFieldKeys == null ? List.of() : List.copyOf(focusFieldKeys);
    }

    public LineageGraphNodeResponse(
            String id, LineageGraphNodeKind kind, LineageGraphNodeSide side, int depth,
            String label, String subtitle, UUID modelId, UUID modelFieldId, UUID taskId,
            UUID dataSourceId, TaskStatus taskStatus, Integer definitionVersion,
            LineageWriteMode writeMode, boolean stale, LineageExternalResourceType externalResourceType,
            UUID resourceId, UUID dataServiceId, DataServiceType dataServiceType,
            DataServiceStatus dataServiceStatus, String routePath
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                externalResourceType, resourceId, dataServiceId, dataServiceType,
                dataServiceStatus, routePath, null, false, List.of());
    }
    public LineageGraphNodeResponse(
            String id,
            LineageGraphNodeKind kind,
            LineageGraphNodeSide side,
            int depth,
            String label,
            String subtitle,
            UUID modelId,
            UUID modelFieldId,
            UUID taskId,
            UUID dataSourceId,
            TaskStatus taskStatus,
            Integer definitionVersion,
            LineageWriteMode writeMode,
            boolean stale,
            LineageExternalResourceType externalResourceType,
            UUID resourceId
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                externalResourceType, resourceId, null, null, null, null, null, false, List.of());
    }

    public LineageGraphNodeResponse(
            String id,
            LineageGraphNodeKind kind,
            LineageGraphNodeSide side,
            int depth,
            String label,
            String subtitle,
            UUID modelId,
            UUID modelFieldId,
            UUID taskId,
            UUID dataSourceId,
            TaskStatus taskStatus,
            Integer definitionVersion,
            LineageWriteMode writeMode,
            boolean stale
    ) {
        this(id, kind, side, depth, label, subtitle, modelId, modelFieldId, taskId,
                dataSourceId, taskStatus, definitionVersion, writeMode, stale,
                null, null, null, null, null, null, null, false, List.of());
    }
}
