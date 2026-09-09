package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricVersionResponse(UUID id, UUID metricId, int version, MetricDefinition definition, List<MetricReferenceSnapshot> references, Instant publishedAt, String publishedBy, String changeNote) {}
