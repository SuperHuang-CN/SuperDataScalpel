package cn.superhuang.data.scalpel.business.compute.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "逐目标注册结果；成功项不因其他项失败而回滚")
public record RegisterComputeTargetsResponse(
        @Schema(description = "与请求顺序一致的逐项结果") List<Item> items) {
    @Schema(description = "单个目标的注册结果")
    public record Item(
            @Schema(description = "目标键") String targetKey,
            @Schema(description = "是否已成功激活或原本已经 ACTIVE") boolean success,
            @Schema(description = "保存后的引擎记录；创建前失败时为空，创建后失败保留 ERROR 记录供重试") ComputeEngineResponse engine,
            @Schema(description = "安全失败说明；成功时为空") String error) { }
}
