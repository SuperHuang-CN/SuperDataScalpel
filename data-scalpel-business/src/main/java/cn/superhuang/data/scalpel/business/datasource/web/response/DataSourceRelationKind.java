package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据源引用方式：DIRECT 直接引用数据源，VIA_MODEL 通过模型物理存储间接引用")
public enum DataSourceRelationKind {
    DIRECT,
    VIA_MODEL
}
