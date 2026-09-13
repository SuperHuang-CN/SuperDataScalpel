package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "物理统计刷新状态：SUCCESS 完整成功，PARTIAL 部分指标可用，FAILED 采集失败，NOT_FOUND 物理表不存在，UNSUPPORTED 当前对象不支持统计")
public enum PhysicalStatisticsRefreshStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
    NOT_FOUND,
    UNSUPPORTED
}
