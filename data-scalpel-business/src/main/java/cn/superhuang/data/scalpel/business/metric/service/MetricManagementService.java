package cn.superhuang.data.scalpel.business.metric.service;

import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class MetricManagementService {
    private final DataMetricRepository metrics;
    private final MetricReleaseRepository releases;
    private final MetricReferenceRepository references;
    private final MetricHealthService health;
    private final DirectoryService directories;
    private final DataModelRepository models;
    private final SearchEngine search;
    private final ObjectMapper mapper;
    private final cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository fields;
    public MetricManagementService(DataMetricRepository metrics,MetricReleaseRepository releases,MetricReferenceRepository references,MetricHealthService health,DirectoryService directories,DataModelRepository models,SearchEngine search,ObjectMapper mapper,cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository fields){
        this.fields=fields;
        this.metrics=metrics;this.releases=releases;this.references=references;this.health=health;this.directories=directories;this.models=models;this.search=search;this.mapper=mapper;
    }
    public PageResponse<MetricResponse> search(SearchRequest request){return search(request,(root,q,cb)->cb.conjunction());}
    public PageResponse<MetricResponse> search(SearchRequest request,Specification<DataMetric> fixed){
        var page=search.search(request,DataMetric.class,metrics,fixed);
        return new PageResponse<>(page.getContent().stream().map(this::response).toList(),page.getTotalElements(),page.getTotalPages(),page.getNumber(),page.getSize());
    }
    public PageResponse<MetricFieldCandidateResponse> fields(UUID modelId,SearchRequest request) {
        if(!models.existsById(modelId))throw error(HttpStatus.NOT_FOUND,"模型不存在");
        var p=search.search(request,cn.superhuang.data.scalpel.business.model.domain.DataModelField.class,fields,(r,q,c)->c.equal(r.get("modelId"),modelId));
        return new PageResponse<>(p.getContent().stream().map(f->new MetricFieldCandidateResponse(f.getId(),f.getCode(),f.getName(),f.getFieldType())).toList(),p.getTotalElements(),p.getTotalPages(),p.getNumber(),p.getSize());
    }
    public DataMetric require(UUID id){return metrics.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"指标不存在"));}
    private DataMetric locked(UUID id){return metrics.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"指标不存在"));}
    public MetricResponse get(UUID id){return response(require(id));}
    public MetricDraftResponse draft(UUID id){var m=require(id);var d=definition(m.getDraftDefinition());var refs=health.references(d);return new MetricDraftResponse(id,d,m.getDraftFingerprint(),health.inspect(m,d,refs,null),refs);}
    public MetricHealthResponse health(UUID id){return get(id).health();}
    @Transactional
    public MetricResponse create(CreateMetricRequest request){
        if(metrics.existsByCode(request.code()))throw error(HttpStatus.CONFLICT,"指标编码已存在");
        directories.validateAssignment(DirectoryScope.METRIC,request.directoryId());
        var m=DataMetric.create(request.code());m.update(request.name().trim(),request.kind(),request.directoryId(),trim(request.ownerName()),trim(request.summary()));
        String json=json(MetricDefinition.empty());m.saveDraft(json,hash(json));return response(metrics.saveAndFlush(m));
    }
    @Transactional
    public MetricResponse update(UUID id,UpdateMetricRequest request){
        var m=locked(id);if(m.getCurrentReleaseId()!=null&&m.getKind()!=request.kind())throw error(HttpStatus.CONFLICT,"首次发布后不能修改指标类型");
        directories.validateAssignment(DirectoryScope.METRIC,request.directoryId());
        m.update(request.name().trim(),request.kind(),request.directoryId(),trim(request.ownerName()),trim(request.summary()));return response(metrics.saveAndFlush(m));
    }
    @Transactional
    public MetricDraftResponse saveDraft(UUID id,UpdateMetricDefinitionRequest request){
        var m=locked(id);checkHash(m,request.expectedDraftFingerprint());
        var d=normalize(request.definition());var next=health.references(d);var previous=health.references(definition(m.getDraftDefinition()));
        for(var r:next){
            boolean existing=previous.stream().anyMatch(p->p.resourceKind()==r.resourceKind()&&p.resourceId().equals(r.resourceId())&&Objects.equals(p.parentModelId(),r.parentModelId())&&Objects.equals(p.targetVersion(),r.targetVersion()));
            if(!existing){requireResourcePermission(r.resourceKind());if(r.contract()==null)throw error(HttpStatus.BAD_REQUEST,"新引用不存在或字段不属于所选模型："+r.path());}
        }
        String json=json(d);m.saveDraft(json,hash(json));project(id,"DRAFT",next);metrics.saveAndFlush(m);return draft(id);
    }
    @Transactional
    public MetricResponse publish(UUID id,PublishMetricRequest request,String author){
        var m=locked(id);checkHash(m,request.expectedDraftFingerprint());var d=definition(m.getDraftDefinition());
        if(d.binding()!=null&&d.binding().modelId()!=null)models.findByIdForUpdate(d.binding().modelId()).orElseThrow(()->error(HttpStatus.CONFLICT,"结果模型已不存在"));
        var refs=health.references(d);for(var r:refs)if(r.resultBinding())requireResourcePermission(r.resourceKind());
        var diagnostic=health.inspect(m,d,refs,null);
        if(!diagnostic.canPublish())throw error(HttpStatus.CONFLICT,diagnostic.issues().stream().filter(MetricIssueResponse::blocking).findFirst().orElseThrow().message());
        String fingerprint=hash(m.getDraftDefinition()+json(refs.stream().filter(MetricReferenceSnapshot::resultBinding).map(r->List.of(r.path(),r.resourceId().toString(),Objects.toString(r.contract(),""))).toList()));
        var last=current(m);MetricRelease release;
        if(last!=null&&last.getDefinitionFingerprint().equals(fingerprint))release=last;
        else release=releases.saveAndFlush(MetricRelease.create(id,last==null?1:last.getVersion()+1,m.getDraftDefinition(),json(refs),fingerprint,author,trim(request.changeNote())));
        m.publish(release.getId());project(id,"CURRENT",refs);metrics.saveAndFlush(m);return response(m);
    }
    @Transactional
    public MetricResponse disable(UUID id){var m=locked(id);if(m.getCurrentReleaseId()==null)throw error(HttpStatus.CONFLICT,"未发布指标不能停用");m.disable();return response(metrics.saveAndFlush(m));}
    @Transactional
    public void delete(UUID id){var m=locked(id);if(m.getCurrentReleaseId()!=null)throw error(HttpStatus.CONFLICT,"曾经发布的指标请使用停用");references.deleteAllByMetricId(id);references.flush();metrics.delete(m);}
    public PageResponse<MetricVersionResponse> versions(UUID id,SearchRequest request){require(id);var p=search.search(request,MetricRelease.class,releases,(r,q,c)->c.equal(r.get("metricId"),id));return new PageResponse<>(p.getContent().stream().map(this::version).toList(),p.getTotalElements(),p.getTotalPages(),p.getNumber(),p.getSize());}
    public MetricVersionResponse version(UUID id,int number){return version(releases.findByMetricIdAndVersion(id,number).orElseThrow(()->error(HttpStatus.NOT_FOUND,"指标版本不存在")));}
    public MetricDefinition effectiveDefinition(DataMetric m){var release=current(m);return definition(release==null?m.getDraftDefinition():release.getDefinitionSnapshot());}
    private MetricResponse response(DataMetric m){var release=current(m);var d=effectiveDefinition(m);var now=health.references(d);var old=release==null?null:snapshots(release.getReferenceSnapshot());
        return new MetricResponse(m.getId(),m.getCode(),m.getName(),m.getKind(),m.getDirectoryId(),m.getOwnerName(),m.getSummary(),m.getStatus(),release==null?null:release.getVersion(),release!=null&&!release.getDefinitionSnapshot().equals(m.getDraftDefinition()),d,health.inspect(m,d,now,old),now,m.getCreatedAt(),m.getUpdatedAt());}
    private MetricVersionResponse version(MetricRelease r){return new MetricVersionResponse(r.getId(),r.getMetricId(),r.getVersion(),definition(r.getDefinitionSnapshot()),snapshots(r.getReferenceSnapshot()),r.getPublishedAt(),r.getPublishedBy(),r.getChangeNote());}
    private MetricRelease current(DataMetric m){return m.getCurrentReleaseId()==null?null:releases.findById(m.getCurrentReleaseId()).orElseThrow(()->new IllegalStateException("指标发布快照缺失"));}
    private void project(UUID id,String scope,List<MetricReferenceSnapshot> refs){references.deleteAllByMetricIdAndScope(id,scope);references.flush();references.saveAll(refs.stream().map(r->MetricReference.create(id,scope,r.path(),r.resourceKind(),r.resourceId(),r.parentModelId(),r.targetVersion(),r.resultBinding())).toList());}
    private static void requireResourcePermission(MetricDefinition.ResourceKind kind){MetricAccess.require(switch(kind){case MODEL,MODEL_FIELD->"model.view";case METRIC->"metric.view";case DATA_SERVICE->"service.view";});}
    private static void checkHash(DataMetric m,String expected){if(!m.getDraftFingerprint().equals(expected))throw error(HttpStatus.CONFLICT,"指标草稿已变更，请刷新后重试");}
    private MetricDefinition definition(String json){return mapper.readValue(json,MetricDefinition.class);}
    private List<MetricReferenceSnapshot> snapshots(String json){return List.of(mapper.readValue(json,MetricReferenceSnapshot[].class));}
    private String json(Object value){return mapper.writeValueAsString(value);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String trim(String s){return s==null||s.isBlank()?null:s.trim();}
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    private static MetricDefinition normalize(MetricDefinition d){return new MetricDefinition(trim(d.businessMeaning()),trim(d.calculation()),trim(d.statisticalScope()),trim(d.timeDescription()),trim(d.sourceGrain()),trim(d.grainDescription()),trim(d.unit()),d.statisticalPeriod(),trim(d.periodFormat()),trim(d.nullHandling()),trim(d.aggregationDescription()),trim(d.updateDescription()),d.decimalPlaces(),d.valueFormat(),d.binding(),d.references());}
}
