package cn.superhuang.data.scalpel.business.metric.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(description = "把当前指标草稿冻结为新的不可变发布版本。")
public record PublishMetricRequest(
        @Schema(description = "客户端读取到的草稿指纹；不一致时拒绝发布或覆盖。")
        @NotBlank String expectedDraftFingerprint,
        @Schema(description = "本次发布的变更说明，写入不可变版本快照，供版本历史审阅。")
        @Size(max=2000) String changeNote
) {}
