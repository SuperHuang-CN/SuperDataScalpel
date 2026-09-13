package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Controls whether a warehouse layer restricts model inputs to an explicit layer list. */
@Schema(description = "输入分层规划策略：UNRESTRICTED 不限制，ALLOW_LIST 只建议读取明确列出的输入分层；当前不阻止任务运行")
public enum ModelWarehouseLayerInputPolicy {
    UNRESTRICTED,
    ALLOW_LIST
}
