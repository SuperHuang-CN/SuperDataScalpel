package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricFieldEvidenceResponse(Integer definitionVersion, boolean currentDefinition, List<UUID> fieldIds, String source) {}
