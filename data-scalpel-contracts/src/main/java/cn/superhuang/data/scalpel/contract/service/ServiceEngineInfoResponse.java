package cn.superhuang.data.scalpel.contract.service;

import java.util.List;

/** Basic, authenticated runtime capability response from an Engine. */
public record ServiceEngineInfoResponse(String code, List<String> databaseTypes) {

    public ServiceEngineInfoResponse {
        databaseTypes = List.copyOf(databaseTypes);
    }
}
