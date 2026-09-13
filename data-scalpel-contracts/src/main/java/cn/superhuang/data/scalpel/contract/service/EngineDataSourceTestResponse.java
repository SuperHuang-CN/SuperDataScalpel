package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Result of testing the API Studio data source currently stored in an Engine. */
public record EngineDataSourceTestResponse(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        @NotBlank String engineCode,
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId,
        @JsonPropertyDescription("JDBC 数据库产品类型，决定驱动、方言和连接校验。")
        @NotBlank String databaseType
) {
}
