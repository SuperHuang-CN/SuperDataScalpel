package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Spark JAR 开发方式：ONLINE 在线 Java 开发；UPLOAD 上传本地构建的 JAR。切换不替换当前制品、不删除源码。")
public enum SparkJarAuthoringMode {
    ONLINE, UPLOAD
}
