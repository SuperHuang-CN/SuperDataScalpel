package cn.superhuang.data.scalpel.business.model.service;

import io.swagger.v3.oas.annotations.media.Schema;

/** Live result of comparing a model definition with its target physical table. */
@Schema(description = "物理表实时状态：NOT_FOUND 不存在，MATCHED 严格匹配，DRIFTED 结构漂移，UNREACHABLE 无法访问，UNSUPPORTED 当前方言或对象不支持")
public enum PhysicalTableState {
    NOT_FOUND,
    MATCHED,
    DRIFTED,
    UNREACHABLE,
    UNSUPPORTED
}
