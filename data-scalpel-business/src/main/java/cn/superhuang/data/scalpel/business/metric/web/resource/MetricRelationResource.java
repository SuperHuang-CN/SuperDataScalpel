package cn.superhuang.data.scalpel.business.metric.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.superhuang.data.scalpel.business.metric.domain.MetricStatus;
import cn.superhuang.data.scalpel.business.metric.service.MetricRelationService;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1")
@Tag(name = "指标关系")
public class MetricRelationResource {
 private final MetricRelationService service;
 public MetricRelationResource(MetricRelationService service){this.service=service;}
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型关联指标")
 @Operation(summary = "查询模型关联指标", description = "分页查询把指定模型作为结果绑定模型（binding.modelId）的指标；口径 references 中仅作为来源、分母或说明引用的模型不会匹配。默认只返回 PUBLISHED 指标；查询 DRAFT 时按当前草稿绑定匹配，查询 DISABLED 时按其当前发布版本绑定匹配，二者都需要指标维护权限。")
 @GetMapping("/models/{modelId}/metrics") @PreAuthorize("hasAuthority('model.view') and hasAuthority('metric.view')")
 public PageResponse<MetricResponse> model(@Parameter(description = "模型 UUID。") @PathVariable UUID modelId,@Parameter(description = "可选状态筛选；为空时默认 PUBLISHED。查询其他状态需要指标维护权限。") @RequestParam(required=false) MetricStatus status,@ParameterObject @ModelAttribute SearchRequest request){return service.modelMetrics(modelId,status,request);}
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务输出模型关联指标")
 @Operation(summary = "查询任务输出模型关联指标", description = "解析任务当前保存定义的输出模型，分页返回把其中任一模型作为结果绑定模型的指标，并附带输出模型及当前未退役血缘快照中的输出字段证据。字段证据只用于解释关系，不参与指标筛选；任务未配置或没有可解析输出时返回明确关系状态。")
 @GetMapping("/tasks/{taskId}/metrics") @PreAuthorize("hasAuthority('task.view') and hasAuthority('model.view') and hasAuthority('metric.view')")
 public MetricTaskRelationsResponse task(@Parameter(description = "任务 UUID。") @PathVariable UUID taskId,@Parameter(description = "可选状态筛选；为空时默认 PUBLISHED。查询其他状态需要指标维护权限。") @RequestParam(required=false) MetricStatus status,@ParameterObject @ModelAttribute SearchRequest request){return service.taskMetrics(taskId,status,request);}
}
