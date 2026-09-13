package cn.superhuang.data.scalpel.business.compute.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "最近一次 Dispatcher 主动检查状态：UNKNOWN 尚未检查或配置已变化；UP 检查成功；DOWN 检查失败或已离线解绑。健康状态不会自动改变注册状态。")
public enum ComputeEngineHealthState {
    UNKNOWN,
    UP,
    DOWN
}
