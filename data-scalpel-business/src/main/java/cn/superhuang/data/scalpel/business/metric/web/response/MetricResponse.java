package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricResponse(UUID id, String code, String name, MetricKind kind, UUID directoryId, String ownerName, String summary, MetricStatus status, Integer publishedVersion, boolean hasDraftChanges, MetricDefinition definition, MetricHealthResponse health, List<MetricReferenceSnapshot> references, Instant createdAt, Instant updatedAt) {}
