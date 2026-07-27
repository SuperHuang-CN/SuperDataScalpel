package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.List;

public record HttpApiInputConfiguration(
        String dataSourceId,
        String resourceId,
        String outputTableName,
        List<HttpApiContracts.RuntimeParameter> runtimeParameters
) {
    public HttpApiInputConfiguration {
        runtimeParameters = runtimeParameters == null ? List.of() : List.copyOf(runtimeParameters);
    }
}
