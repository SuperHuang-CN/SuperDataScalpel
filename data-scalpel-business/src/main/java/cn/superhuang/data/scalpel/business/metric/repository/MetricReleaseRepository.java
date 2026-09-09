package cn.superhuang.data.scalpel.business.metric.repository;
import cn.superhuang.data.scalpel.business.metric.domain.MetricRelease;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.*;
public interface MetricReleaseRepository extends SearchRepository<MetricRelease,UUID> {
 Optional<MetricRelease> findByMetricIdAndVersion(UUID metricId,int version);
}
