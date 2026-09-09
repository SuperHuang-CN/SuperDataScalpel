package cn.superhuang.data.scalpel.business.metric.repository;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface MetricReferenceRepository extends JpaRepository<MetricReference,UUID> {
 void deleteAllByMetricIdAndScope(UUID metricId,String scope);
 void deleteAllByMetricId(UUID metricId);
 List<MetricReference> findAllByMetricIdAndScope(UUID metricId,String scope);
 @Query("select r from MetricReference r where r.resourceId in :ids and r.resultBinding=true and r.scope='CURRENT' and r.metricId in (select m.id from DataMetric m where m.status=cn.superhuang.data.scalpel.business.metric.domain.MetricStatus.PUBLISHED)")
 List<MetricReference> findProtected(@Param("ids") Collection<UUID> ids);
}
