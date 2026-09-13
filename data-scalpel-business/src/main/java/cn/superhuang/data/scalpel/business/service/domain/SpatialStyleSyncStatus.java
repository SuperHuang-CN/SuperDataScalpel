package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "空间样式与 GeoServer 的同步状态：NOT_APPLIED 尚无远端应用版本；OUT_OF_SYNC 本地版本较新；SYNCING 正在应用；IN_SYNC 当前版本已确认应用；SYNC_FAILED 当前版本应用失败。")
public enum SpatialStyleSyncStatus {
    NOT_APPLIED,
    OUT_OF_SYNC,
    SYNCING,
    IN_SYNC,
    SYNC_FAILED
}
