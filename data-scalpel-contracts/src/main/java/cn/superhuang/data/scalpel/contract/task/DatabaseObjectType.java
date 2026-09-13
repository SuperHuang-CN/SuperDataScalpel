package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("数据源资源对象类型：数据库 TABLE、VIEW、TDengine SUPERTABLE、已纳管 HTTP API_RESOURCE 或空间 SPATIAL_FEATURE_RESOURCE。")
public enum DatabaseObjectType {
    TABLE,
    VIEW,
    SUPERTABLE,
    API_RESOURCE,
    SPATIAL_FEATURE_RESOURCE
}
