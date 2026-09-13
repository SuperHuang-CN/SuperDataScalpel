package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** States whether a model creates a new table or explicitly binds an existing one. */
@Schema(description = "模型物理表模式：MANAGED 由平台按模型契约受控创建，EXTERNAL 只绑定并校验已有表")
public enum PhysicalTableMode {
    MANAGED,
    EXTERNAL
}
