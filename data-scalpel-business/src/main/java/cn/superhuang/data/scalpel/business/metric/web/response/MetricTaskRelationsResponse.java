package cn.superhuang.data.scalpel.business.metric.web.response;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
public record MetricTaskRelationsResponse(UUID taskId, Integer definitionVersion, String resolution, PageResponse<MetricResponse> metrics, List<cn.superhuang.data.scalpel.business.task.web.response.TaskRelatedModelResponse> outputModels, List<MetricFieldEvidenceResponse> fieldEvidence) {}
