package cn.superhuang.data.scalpel.business.service.consumer.credential.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API Key 在网关中的状态：SYNC_PENDING 正在同步或待确认；ACTIVE 当前密钥修订已生效；SYNC_FAILED 最近同步失败；DELETE_PENDING 正在删除；DELETE_FAILED 最近删除失败。")
public enum GatewayCredentialStatus {
    SYNC_PENDING,
    ACTIVE,
    SYNC_FAILED,
    DELETE_PENDING,
    DELETE_FAILED
}
