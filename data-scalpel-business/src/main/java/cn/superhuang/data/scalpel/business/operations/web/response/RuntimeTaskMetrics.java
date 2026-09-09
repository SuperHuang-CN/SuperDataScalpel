package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import java.util.*;
public record RuntimeTaskMetrics(Map<TaskRunStatus, Long> current, Map<TaskRunStatus, Long> completed,
                                 long qualityFailed, Double successRate, List<RuntimeTrendResponse> trend) {}
