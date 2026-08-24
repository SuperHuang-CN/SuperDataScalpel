package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record HttpApiInputConfiguration(
        String dataSourceId,
        List<HttpApiInputResourceSelection> resources
) {
    public HttpApiInputConfiguration {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public HttpApiInputConfiguration(
            String dataSourceId,
            String resourceId,
            String outputTableName,
            List<cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts.RuntimeParameter> runtimeParameters
    ) {
        this(dataSourceId, List.of(new HttpApiInputResourceSelection(
                resourceId, outputTableName, runtimeParameters)));
    }
}
