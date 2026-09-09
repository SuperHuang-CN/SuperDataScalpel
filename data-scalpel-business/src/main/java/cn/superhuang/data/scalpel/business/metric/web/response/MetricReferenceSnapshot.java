package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricReferenceSnapshot(String path, MetricDefinition.ResourceKind resourceKind, UUID resourceId, UUID parentModelId, Integer targetVersion, String name, String code, String status, String contract, boolean resultBinding) {}
