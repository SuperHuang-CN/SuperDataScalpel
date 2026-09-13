package cn.superhuang.data.scalpel.business.compute.web.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "反注册计算引擎请求；请求体可省略，此时等同于 force=false。")
public record DeactivateComputeEngineRequest(
        @Schema(description = "是否要求 Dispatcher 取消排队和运行中的执行后再反注册。false 时存在任何活动执行即返回冲突；true 也可能在取消未及时收敛时返回冲突并保持 DRAINING。")
        boolean force
) {
}
