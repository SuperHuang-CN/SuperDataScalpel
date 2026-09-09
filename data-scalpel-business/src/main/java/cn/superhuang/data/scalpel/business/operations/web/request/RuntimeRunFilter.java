package cn.superhuang.data.scalpel.business.operations.web.request;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record RuntimeRunFilter(@Size(max=150) String taskName, UUID directoryId, boolean activeOnly,
                               Instant from, Instant to, @Pattern(regexp="queuedAt|endedAt") String timeField,
                               TaskRunExecutionMode mode, boolean batchOnly) {}
