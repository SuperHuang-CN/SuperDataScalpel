package cn.superhuang.data.scalpel.business.panorama.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
@Schema(description = "对处理失败的当前候选内容执行重试或放弃操作的请求。")
public record PanoramaCandidateRequest(
        @Schema(description = "从最新全景详情的 candidateContent.id 取得的候选内容 UUID；必须仍是当前候选且 processingStatus=FAILED。")
        @NotNull UUID candidateId
) {}
