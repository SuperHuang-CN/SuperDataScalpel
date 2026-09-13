package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Idempotent response for an Engine access-policy application request. */
public record EngineAccessPolicyApplyResponse(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        String engineCode,
        @JsonPropertyDescription("Engine 已确认持久化并应用的策略修订号；应与请求 revision 相同。")
        long revision,
        @JsonPropertyDescription("引擎返回的策略应用状态；当前成功值固定为 READY。")
        String status
) {
}
