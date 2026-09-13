package cn.superhuang.data.scalpel.business.service.gateway.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据服务网关绑定状态：PUBLISHING 同步创建或更新已开始；PUBLISHED 已确认发布；PUBLISH_FAILED 最近发布失败，远端是否留下部分对象不确定；REMOVING 同步撤回已开始；REMOVE_FAILED 最近撤回失败。成功撤回会直接删除绑定记录，不保留 REMOVED 状态；失败不自动重试。")
public enum GatewayServicePublicationStatus {
    PUBLISHING,
    PUBLISHED,
    PUBLISH_FAILED,
    REMOVING,
    REMOVE_FAILED
}
