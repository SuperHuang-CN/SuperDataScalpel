package cn.superhuang.data.scalpel.business.datasource.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** The business roles a data connection can serve in the platform. */
@Schema(description = "数据源业务用途：SOURCE 作为任务输入，STORAGE 作为模型物理存储，DISTRIBUTION 作为任务分发目标")
public enum DataSourcePurpose {
    SOURCE,
    STORAGE,
    DISTRIBUTION
}
