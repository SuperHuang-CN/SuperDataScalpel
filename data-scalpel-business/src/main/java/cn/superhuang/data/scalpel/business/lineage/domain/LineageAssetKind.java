package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "血缘资产种类：MODEL 为纳管数据模型，JDBC_TABLE 为直接引用的 JDBC 物理表，EXTERNAL_RESOURCE 为 Kafka、文件、HTTP、空间服务、对象存储或查询结果等其他来源。")
public enum LineageAssetKind {
    MODEL,
    JDBC_TABLE,
    EXTERNAL_RESOURCE
}
