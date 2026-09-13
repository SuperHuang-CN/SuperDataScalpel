package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.List;

/** One HTTP API resource and its task-local, non-sensitive parameters. */
@JsonClassDescription("HTTP API 输入节点中的一次资源调用；Canvas 只绑定已纳管资源、非敏感固定参数和输出表名，不复制 URL、请求模板、凭据、分页规则或输出 Schema。")
public record HttpApiInputResourceSelection(
        @JsonPropertyDescription("HTTP API 资源 UUID 字符串；草稿可为空，编译前必须解析为 dataSourceId 下已启用且具有完整输出字段声明的资源。")
        String resourceId,
        @JsonPropertyDescription("本次资源调用生成的 BOUNDED Canvas 逻辑表名；必须非空，并与本节点其他资源的输出表名不同。")
        String outputTableName,
        @JsonPropertyDescription("替换资源请求模板变量的任务级固定非敏感参数；会随 Canvas 定义明文持久化，名称必须与模板声明一致且不能重复。不得在此保存 Token、密码、API Key 或其他凭据。")
        List<HttpApiContracts.RuntimeParameter> runtimeParameters
) {
    public HttpApiInputResourceSelection {
        runtimeParameters = runtimeParameters == null ? List.of() : List.copyOf(runtimeParameters);
    }
}
