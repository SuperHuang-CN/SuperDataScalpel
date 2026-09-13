package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Stable, user-safe category for an execution failure. */
@JsonClassDescription("执行错误安全分类：CONFIGURATION 配置；CONNECTION 连接；AUTHENTICATION 认证；PERMISSION 权限；SCHEMA Schema；CONSTRAINT 数据约束；TIMEOUT 超时；CANCELLED 取消；RESOURCE 计算或存储资源；EXTERNAL_SYSTEM 外部系统；INTERNAL 平台内部。")
public enum ExecutionErrorCategory {
    CONFIGURATION,
    CONNECTION,
    AUTHENTICATION,
    PERMISSION,
    SCHEMA,
    CONSTRAINT,
    TIMEOUT,
    CANCELLED,
    RESOURCE,
    EXTERNAL_SYSTEM,
    INTERNAL
}
