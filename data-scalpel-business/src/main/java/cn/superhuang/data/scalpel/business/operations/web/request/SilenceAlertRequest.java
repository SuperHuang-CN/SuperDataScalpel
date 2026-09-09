package cn.superhuang.data.scalpel.business.operations.web.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record SilenceAlertRequest(@NotNull @Future Instant untilAt, @NotBlank @Size(max=1000) String reason) {}
