package cn.superhuang.data.scalpel.business.ontology.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "按关系和方向分页读取具体关联对象的只读请求。")
public record BusinessObjectRelatedPreviewRequest(
        @Schema(description = "当前对象业务唯一标识值。") @NotBlank @Size(max = 500) String objectKey,
        @Schema(description = "关系稳定 UUID。") @NotNull UUID relationId,
        @Schema(description = "OUTBOUND 从当前对象到目标对象；INBOUND 从当前对象查看反向关联对象。") @NotNull Direction direction,
        @Schema(description = "页码，从 1 开始；省略时为 1。") @Min(1) Integer pageNo,
        @Schema(description = "每页对象数，范围 1 到 100；省略时为 20。") @Min(1) @Max(100) Integer pageSize
) {
    public enum Direction {
        OUTBOUND,
        INBOUND
    }
}
