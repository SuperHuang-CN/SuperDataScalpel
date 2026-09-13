package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "通知事件类型：TRIGGERED 新异常成立；RECOVERED 持续异常已有有效恢复证据；TEST 用户显式发起的渠道测试。事件型运行失败和质检失败不会自动产生 RECOVERED。")
public enum AlertEventType {
    TRIGGERED, RECOVERED, TEST
}
