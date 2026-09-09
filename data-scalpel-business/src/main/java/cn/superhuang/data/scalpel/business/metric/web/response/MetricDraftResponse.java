package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricDraftResponse(UUID metricId, MetricDefinition definition, String fingerprint, MetricHealthResponse health, List<MetricReferenceSnapshot> references) {}
