package cn.superhuang.data.scalpel.business.metric.web.request;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record UpdateMetricDefinitionRequest(@NotNull @Valid MetricDefinition definition, @NotBlank String expectedDraftFingerprint) {}
