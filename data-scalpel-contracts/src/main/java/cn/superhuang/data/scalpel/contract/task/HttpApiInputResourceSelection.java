package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.List;

/** One HTTP API resource and its task-local, non-sensitive parameters. */
public record HttpApiInputResourceSelection(
        String resourceId,
        String outputTableName,
        List<HttpApiContracts.RuntimeParameter> runtimeParameters
) {
    public HttpApiInputResourceSelection {
        runtimeParameters = runtimeParameters == null ? List.of() : List.copyOf(runtimeParameters);
    }
}
