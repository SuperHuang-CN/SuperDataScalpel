package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Basic, authenticated runtime capability response from an Engine. */
public record ServiceEngineInfoResponse(
        @JsonPropertyDescription("逻辑 Service Engine 的稳定编码，Admin 用于校验连接归属；同一 PostgreSQL 协调组的副本使用相同编码，不同组必须独立运行库。")
        String code,
        @JsonPropertyDescription("该 Service Engine 声明支持的数据库类型集合。")
        List<String> databaseTypes
) {

    public ServiceEngineInfoResponse {
        databaseTypes = List.copyOf(databaseTypes);
    }
}
