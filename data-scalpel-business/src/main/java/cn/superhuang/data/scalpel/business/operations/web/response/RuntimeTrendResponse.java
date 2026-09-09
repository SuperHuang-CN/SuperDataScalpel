package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import java.time.Instant;
public record RuntimeTrendResponse(Instant from, Instant to, TaskRunStatus status, long count) {}
