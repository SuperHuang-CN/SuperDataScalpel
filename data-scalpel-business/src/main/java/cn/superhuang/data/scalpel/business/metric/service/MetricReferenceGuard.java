package cn.superhuang.data.scalpel.business.metric.service;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class MetricReferenceGuard {
 private final MetricReferenceRepository references;
 private final DataMetricRepository metrics;
 private final DataModelRepository models;
 public MetricReferenceGuard(MetricReferenceRepository references,DataMetricRepository metrics,DataModelRepository models){this.references=references;this.metrics=metrics;this.models=models;}
 @Transactional
 public void assertFieldsRemovable(UUID modelId,Collection<UUID> fieldIds){
  if(fieldIds.isEmpty())return;
  models.findByIdForUpdate(modelId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"模型不存在"));
  var ids=references.findProtected(fieldIds).stream().map(r->r.getMetricId()).distinct().toList();
  if(!ids.isEmpty())throw new cn.superhuang.data.scalpel.web.error.CodedProblemException(HttpStatus.CONFLICT,"MODEL_REFERENCED","字段仍被已发布指标引用，请先调整绑定或停用指标"+(MetricAccess.has("metric.view")?"："+String.join("、",metrics.findAllById(ids).stream().map(m->m.getName()).toList()):""));
 }
}
