package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "流式输出类型：KAFKA 写入主题；JDBC 写入数据库；CUSTOM 由用户 JAR 自行处理。")
public enum StreamingSinkType {
    KAFKA,
    JDBC,
    CUSTOM
}
