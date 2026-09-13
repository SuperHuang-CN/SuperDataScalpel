package cn.superhuang.data.scalpel.business.service.consumer.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API 消费者与网关对象的同步状态：SYNC_PENDING 正在同步或待确认；SYNCED 当前修订已确认同步；SYNC_FAILED 最近同步失败；DELETE_PENDING 正在删除；DELETE_FAILED 最近删除失败。")
public enum GatewayConsumerSyncStatus {
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED,
    DELETE_PENDING,
    DELETE_FAILED
}
