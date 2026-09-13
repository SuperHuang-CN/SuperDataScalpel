package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Source-defined physical role retained for external models such as TDengine supertables. */
@Schema(description = "外部物理列角色：REGULAR 普通列，TIME_KEY 时间主键，TAG TDengine 标签列")
public enum DataModelPhysicalColumnRole {
    REGULAR,
    TIME_KEY,
    TAG
}
