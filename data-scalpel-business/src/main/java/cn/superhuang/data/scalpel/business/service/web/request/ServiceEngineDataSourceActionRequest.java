package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
/** Explicit empty action payload for Engine data source synchronization commands. */
@Schema(description = "服务引擎数据源同步、连接测试或删除命令的显式空请求体。")
public record ServiceEngineDataSourceActionRequest() {
}
