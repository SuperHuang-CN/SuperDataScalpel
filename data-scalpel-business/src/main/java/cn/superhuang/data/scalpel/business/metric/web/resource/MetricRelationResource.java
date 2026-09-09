package cn.superhuang.data.scalpel.business.metric.web.resource;
import cn.superhuang.data.scalpel.business.metric.domain.MetricStatus;
import cn.superhuang.data.scalpel.business.metric.service.MetricRelationService;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1")
public class MetricRelationResource {
 private final MetricRelationService service;
 public MetricRelationResource(MetricRelationService service){this.service=service;}
 @GetMapping("/models/{modelId}/metrics") @PreAuthorize("hasAuthority('model.view') and hasAuthority('metric.view')")
 public PageResponse<MetricResponse> model(@PathVariable UUID modelId,@RequestParam(required=false) MetricStatus status,@ModelAttribute SearchRequest request){return service.modelMetrics(modelId,status,request);}
 @GetMapping("/tasks/{taskId}/metrics") @PreAuthorize("hasAuthority('task.view') and hasAuthority('model.view') and hasAuthority('metric.view')")
 public MetricTaskRelationsResponse task(@PathVariable UUID taskId,@RequestParam(required=false) MetricStatus status,@ModelAttribute SearchRequest request){return service.taskMetrics(taskId,status,request);}
}
