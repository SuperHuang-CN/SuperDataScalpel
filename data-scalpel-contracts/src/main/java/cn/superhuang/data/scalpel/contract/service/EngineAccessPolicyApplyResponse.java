package cn.superhuang.data.scalpel.contract.service;

/** Idempotent response for an Engine access-policy application request. */
public record EngineAccessPolicyApplyResponse(String engineCode, long revision, String status) {
}
