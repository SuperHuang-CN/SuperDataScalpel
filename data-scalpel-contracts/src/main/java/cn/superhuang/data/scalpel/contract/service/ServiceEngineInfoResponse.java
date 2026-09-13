package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Basic, authenticated runtime capability response from an Engine. */
public record ServiceEngineInfoResponse(
        @JsonPropertyDescription("Service Engine 实例的稳定编码，Admin 可用它校验连接到预期实例。")
        String code,
        @JsonPropertyDescription("该 Service Engine 声明支持的数据库类型集合。")
        List<String> databaseTypes
) {

    public ServiceEngineInfoResponse {
        databaseTypes = List.copyOf(databaseTypes);
    }
}
