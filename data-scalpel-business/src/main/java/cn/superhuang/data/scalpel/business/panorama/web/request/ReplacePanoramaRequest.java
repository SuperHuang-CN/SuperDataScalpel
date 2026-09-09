package cn.superhuang.data.scalpel.business.panorama.web.request;

import jakarta.validation.constraints.*;
import java.util.UUID;
public record ReplacePanoramaRequest(@NotNull UUID clientRequestId, @NotNull @Min(0) Long expectedContentVersion) {}
