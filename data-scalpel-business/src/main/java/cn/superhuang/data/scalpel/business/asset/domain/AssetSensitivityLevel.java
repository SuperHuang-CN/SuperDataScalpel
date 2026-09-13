package cn.superhuang.data.scalpel.business.asset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "资产的治理敏感级别：PUBLIC 公开数据；INTERNAL 内部使用；SENSITIVE 敏感数据。该字段用于门户标识，不改变匿名门户可见性，也不构成访问控制。")
public enum AssetSensitivityLevel {
    PUBLIC,
    INTERNAL,
    SENSITIVE
}
