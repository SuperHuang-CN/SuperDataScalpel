package cn.superhuang.data.scalpel.business.metric.web.request;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CreateMetricRequest(@NotBlank @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String code, @NotBlank @Size(max=100) String name, @NotNull MetricKind kind, UUID directoryId, @Size(max=100) String ownerName, @Size(max=1000) String summary) {}
