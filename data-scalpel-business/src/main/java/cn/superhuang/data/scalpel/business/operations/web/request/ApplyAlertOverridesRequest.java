package cn.superhuang.data.scalpel.business.operations.web.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record ApplyAlertOverridesRequest(@NotEmpty @Size(max=100) List<@NotNull UUID> subjectIds,
                                        @NotNull @Valid SaveAlertRuleRequest configuration) {}
