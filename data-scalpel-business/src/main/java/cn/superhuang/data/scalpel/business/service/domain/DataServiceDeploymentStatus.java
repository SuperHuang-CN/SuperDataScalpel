package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据服务在运行引擎中的部署状态：PENDING 同步部署调用已经登记但尚未确认，也可能是进程中断遗留；DEPLOYED 已确认当前修订部署；FAILED 最近一次部署或移除失败；REMOVING 同步移除调用已开始；REMOVED 已确认移除。系统不后台自动重试，需再次启用、停用或清理。")
public enum DataServiceDeploymentStatus {
    PENDING,
    DEPLOYED,
    FAILED,
    REMOVING,
    REMOVED
}
