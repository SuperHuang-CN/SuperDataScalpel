package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "模型删除和字段变更前使用的当前保护性引用汇总。它聚合已保存任务定义、当前未退役血缘、数据服务定义、已发布指标和业务对象类型当前定义引用，不包含历史运行快照。")
public record DataModelReferencesResponse(
        @Schema(description = "模型 UUID") UUID modelId,
        @Schema(description = "当前是否完全没有任务、服务、已发布指标或业务对象类型保护性引用；不检查模型生命周期。PUBLISHED 模型即使为 true 仍须先停用才能删除。") boolean deletable,
        @Schema(description = "已保存任务定义及当前未退役任务血缘中的引用位置；可能包含相同任务的多个角色或来源。") List<DataModelReferenceTaskResponse> tasks,
        @Schema(description = "当前数据服务定义中的模型引用；服务生命周期状态不影响其作为删除保护。") List<DataModelReferenceServiceResponse> services,
        @Schema(description = "已发布指标中的模型或字段引用。调用用户缺少 metric.view 时列表为空，但这些隐藏引用仍会使 deletable=false。") List<DataModelReferenceMetricResponse> metrics,
        @Schema(description = "业务对象类型当前定义中的模型或字段引用。调用用户缺少 ontology.view 时列表为空，但这些隐藏引用仍会使 deletable=false。") List<DataModelReferenceBusinessObjectTypeResponse> businessObjectTypes
) {
    public DataModelReferencesResponse(UUID modelId,boolean deletable,List<DataModelReferenceTaskResponse> tasks,List<DataModelReferenceServiceResponse> services) { this(modelId,deletable,tasks,services,List.of(), List.of()); }
    public DataModelReferencesResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        businessObjectTypes = businessObjectTypes == null ? List.of() : List.copyOf(businessObjectTypes);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        services = services == null ? List.of() : List.copyOf(services);
    }
}
