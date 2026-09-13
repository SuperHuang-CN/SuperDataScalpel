package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理 HTTP API 输入配置；从一个已启用且具有 SOURCE 用途的 HTTP_API 数据源调用一个或多个已纳管资源，每项按资源声明的 Schema 产生一张 BOUNDED 表。请求、认证、签名、分页和异步轮询由资源定义负责。")

public record HttpApiInputConfiguration(
        @JsonPropertyDescription("提供资源的 HTTP API 数据源 UUID 字符串；草稿可为空，编译前必须解析为已启用、具有 SOURCE 用途的 HTTP_API 数据源。")
        String dataSourceId,
        @JsonPropertyDescription("按顺序调用的资源；至少一项，同一资源 UUID 不能重复且必须属于 dataSourceId。各 outputTableName 在节点内必须唯一；Compiler 只使用声明 Schema，不请求远端。")
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
