package cn.superhuang.data.scalpel.business.metric;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.metric.service.*;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.model.repository.*;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class MetricLifecycleTest {
 private final DataMetricRepository metrics=mock(DataMetricRepository.class);
 private final MetricReleaseRepository releases=mock(MetricReleaseRepository.class);
 private final MetricReferenceRepository references=mock(MetricReferenceRepository.class);
 private final DataModelRepository models=mock(DataModelRepository.class);
 private final DataModelFieldRepository fields=mock(DataModelFieldRepository.class);
 private final Map<UUID,DataMetric> state=new HashMap<>();
 private final Map<UUID,MetricRelease> history=new HashMap<>();
 private MetricManagementService service;
 @BeforeEach void prepare(){
  var health=new MetricHealthService(models,fields,metrics,releases,mock(DataServiceRepository.class));
  service=new MetricManagementService(metrics,releases,references,health,mock(DirectoryService.class),models,new SearchEngine(),JsonMapper.builder().build(),fields);
  when(metrics.saveAndFlush(any())).thenAnswer(i->{DataMetric m=i.getArgument(0);identify(m);state.put(m.getId(),m);return m;});
  when(metrics.findById(any())).thenAnswer(i->Optional.ofNullable(state.get(i.getArgument(0))));
  when(metrics.findByIdForUpdate(any())).thenAnswer(i->Optional.ofNullable(state.get(i.getArgument(0))));
  when(releases.saveAndFlush(any())).thenAnswer(i->{MetricRelease r=i.getArgument(0);identify(r);history.put(r.getId(),r);return r;});
  when(releases.findById(any())).thenAnswer(i->Optional.ofNullable(history.get(i.getArgument(0))));
  when(releases.findByMetricIdAndVersion(any(),anyInt())).thenAnswer(i->history.values().stream().filter(r->r.getMetricId().equals(i.getArgument(0))&&r.getVersion()==(int)i.getArgument(1)).findFirst());
 }
 @Test void draftReleaseAndDisableKeepIndependentImmutableVersions(){
  UUID id=create();var saved=service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("统计有效办件"),service.draft(id).fingerprint()));
  var first=service.publish(id,new PublishMetricRequest(saved.fingerprint(),"首次"),"admin");
  assertEquals(1,first.publishedVersion());assertEquals("UNBOUND",first.health().bindingStatus());assertEquals(MetricStatus.PUBLISHED,first.status());
  assertEquals(1,service.publish(id,new PublishMetricRequest(saved.fingerprint(),"重复提交"),"admin").publishedVersion());
  service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("去重后统计有效办件"),saved.fingerprint()));
  assertEquals("统计有效办件",service.get(id).definition().calculation());assertTrue(service.get(id).hasDraftChanges());
  var second=service.publish(id,new PublishMetricRequest(service.draft(id).fingerprint(),"调整去重"),"admin");assertEquals(2,second.publishedVersion());
  assertEquals("统计有效办件",service.version(id,1).definition().calculation());
  service.disable(id);assertEquals(MetricStatus.DISABLED,service.get(id).status());
  assertEquals(2,service.publish(id,new PublishMetricRequest(service.draft(id).fingerprint(),"恢复"),"admin").publishedVersion());
  service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("统计有效办件"),service.draft(id).fingerprint()));
  assertEquals(3,service.publish(id,new PublishMetricRequest(service.draft(id).fingerprint(),"恢复旧口径"),"admin").publishedVersion());
  assertThrows(ResponseStatusException.class,()->service.delete(id));assertEquals(3,history.size());
  verifyNoInteractions(models,fields);
 }
 @Test void incompleteDraftCanSaveButCannotPublishAndStaleWritesConflict(){
  UUID id=create();var original=service.draft(id);assertFalse(original.health().canPublish());
  assertThrows(ResponseStatusException.class,()->service.publish(id,new PublishMetricRequest(original.fingerprint(),null),"admin"));
  service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("规则A"),original.fingerprint()));
  assertThrows(ResponseStatusException.class,()->service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("规则B"),original.fingerprint())));
  assertThrows(ResponseStatusException.class,()->service.publish(id,new PublishMetricRequest(original.fingerprint(),null),"admin"));assertTrue(history.isEmpty());
 }
 @Test void publishingFixesKindAndDeletionOfDraftDoesNotTouchModels(){
  UUID id=create();service.delete(id);verify(metrics).delete(state.get(id));verifyNoInteractions(models,fields);
  var draft=service.saveDraft(id,new UpdateMetricDefinitionRequest(definition("规则"),service.draft(id).fingerprint()));service.publish(id,new PublishMetricRequest(draft.fingerprint(),null),"admin");
  assertThrows(ResponseStatusException.class,()->service.update(id,new UpdateMetricRequest("指标",MetricKind.ATOMIC,null,"负责人",null)));
 }
 private UUID create(){return service.create(new CreateMetricRequest("monthly_cases","月度办结",MetricKind.DERIVED,null,"负责人",null)).id();}
 private static void identify(BaseEntity entity){if(entity.getId()==null)ReflectionTestUtils.setField(entity,"id",UUID.randomUUID());}
 private static MetricDefinition definition(String calculation){return new MetricDefinition("办结件数",calculation,"排除测试记录","按完成月份归属",null,"月份 × 区县","件",MetricDefinition.Period.MONTH,"yyyy-MM","缺失为空","互斥地区可求和",null,0,MetricDefinition.ValueFormat.NUMBER,null,List.of());}
}
