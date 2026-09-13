package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("数据库表唯一键来源：PRIMARY_KEY 为主键约束，UNIQUE_INDEX 为唯一索引；字段顺序按数据库元数据保存。")
public enum MetadataUniqueKeyType {
    PRIMARY_KEY,
    UNIQUE_INDEX
}
