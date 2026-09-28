package cn.superhuang.datascalpel.sdk;

/**
 * 平台分配的流式查询配置，直接应用到 Writer。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @param queryName 平台分配的查询名称，不为空。
 * @param checkpointLocation 平台管理的 Checkpoint 路径，不为空，不要自行覆盖。
 */
public record StreamingQuerySpec(String queryName, String checkpointLocation) {
    public StreamingQuerySpec {
        if (queryName == null || queryName.isBlank()) {
            throw new IllegalArgumentException("queryName must not be blank");
        }
        if (checkpointLocation == null || checkpointLocation.isBlank()) {
            throw new IllegalArgumentException("checkpointLocation must not be blank");
        }
    }
}
