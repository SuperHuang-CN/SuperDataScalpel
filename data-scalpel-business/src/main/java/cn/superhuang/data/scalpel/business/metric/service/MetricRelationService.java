package cn.superhuang.data.scalpel.business.metric.service;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.business.task.service.TaskModelRelationQueryService;
import cn.superhuang.data.scalpel.business.task.web.response.*;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.lineage.repository.*;
import cn.superhuang.data.scalpel.business.lineage.domain.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class MetricRelationService {
    private final MetricManagementService management;
    private final TaskModelRelationQueryService tasks;
    private final DataModelRepository models;
    private final TaskLineageSnapshotRepository snapshots;
    private final TaskLineageAssetRepository assets;
    private final TaskLineageAssetFieldRepository fields;
    public MetricRelationService(MetricManagementService management,TaskModelRelationQueryService tasks,DataModelRepository models,TaskLineageSnapshotRepository snapshots,TaskLineageAssetRepository assets,TaskLineageAssetFieldRepository fields){this.management=management;this.tasks=tasks;this.models=models;this.snapshots=snapshots;this.assets=assets;this.fields=fields;}
    public PageResponse<MetricResponse> modelMetrics(UUID modelId,MetricStatus status,SearchRequest request){
        if(!models.existsById(modelId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模型不存在");
        return management.search(request,matching(List.of(modelId),visibleStatus(status)));
    }
    public MetricTaskRelationsResponse taskMetrics(UUID taskId,MetricStatus status,SearchRequest request){
        var relation=tasks.getTaskModelRelations(taskId);
        var outputs=relation.models().stream().filter(m->m.roles().contains(ModelTaskRelationRole.OUTPUT)).map(m->new TaskRelatedModelResponse(m.modelId(),m.modelCode(),m.modelName(),m.modelStatus(),m.physicalTableMode(),m.schemaVersion(),List.of(ModelTaskRelationRole.OUTPUT),m.locations().stream().filter(l->l.role()==ModelTaskRelationRole.OUTPUT).toList())).toList();
        var ids=outputs.stream().map(TaskRelatedModelResponse::modelId).toList();
        var result=management.search(request,matching(ids,visibleStatus(status)));
        List<MetricFieldEvidenceResponse> evidence=new ArrayList<>();
        snapshots.findFirstByTaskIdAndRetiredAtIsNullOrderByGenerationDesc(taskId).ifPresent(s->{
            var outputAssets=assets.findAllBySnapshotIdIn(List.of(s.getId())).stream().filter(a->a.getRole()==LineageAssetRole.OUTPUT&&ids.contains(a.getModelId())).map(TaskLineageAsset::getId).toList();
            var fieldIds=outputAssets.isEmpty()?List.<UUID>of():fields.findAllByAssetIdIn(outputAssets).stream().map(TaskLineageAssetField::getModelFieldId).filter(Objects::nonNull).distinct().toList();
            if(!fieldIds.isEmpty())evidence.add(new MetricFieldEvidenceResponse(s.getDefinitionVersion(),Objects.equals(relation.definitionVersion(),s.getDefinitionVersion()),fieldIds,"EXISTING_LINEAGE"));
        });
        return new MetricTaskRelationsResponse(taskId,relation.definitionVersion(),!relation.configured()?"UNCONFIGURED":outputs.isEmpty()?"NO_RESOLVABLE_OUTPUT":"MODEL_RELATIONS",result,outputs,List.copyOf(evidence));
    }
    public PageResponse<ModelRelatedTaskResponse> metricTasks(UUID metricId,SearchRequest request){
        var m=management.require(metricId);var binding=management.effectiveDefinition(m).binding();
        if(binding==null||binding.modelId()==null||!models.existsById(binding.modelId()))return new PageResponse<>(List.of(),0,0,request.page()==null?0:request.page(),request.size()==null?20:request.size());
        var p=tasks.searchRelatedTasks(binding.modelId(),ModelTaskRelationRole.OUTPUT,request);
        return new PageResponse<>(p.content().stream().map(t->new ModelRelatedTaskResponse(t.taskId(),t.taskName(),t.taskType(),t.taskStatus(),t.definitionVersion(),List.of(ModelTaskRelationRole.OUTPUT),t.locations().stream().filter(l->l.role()==ModelTaskRelationRole.OUTPUT).toList(),t.updatedAt())).toList(),p.totalElements(),p.totalPages(),p.page(),p.size());
    }
    private MetricStatus visibleStatus(MetricStatus status){var s=status==null?MetricStatus.PUBLISHED:status;if(s!=MetricStatus.PUBLISHED)MetricAccess.require("metric.manage");return s;}
    private Specification<DataMetric> matching(List<UUID> modelIds,MetricStatus status){return (root,q,cb)->{
        if(modelIds.isEmpty())return cb.disjunction();
        var sub=q.subquery(UUID.class);var ref=sub.from(MetricReference.class);
        sub.select(ref.get("metricId")).where(ref.get("resourceId").in(modelIds),cb.equal(ref.get("resourceKind"),MetricDefinition.ResourceKind.MODEL),cb.equal(ref.get("referencePath"),"binding.modelId"),cb.equal(ref.get("scope"),status==MetricStatus.DRAFT?"DRAFT":"CURRENT"));
        return cb.and(cb.equal(root.get("status"),status),root.get("id").in(sub));
    };}
}
