package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "建立一个服务引擎与平台数据源的唯一登记，并在同一 HTTP 调用中立即尝试向引擎同步当前 JDBC 连接快照。")

public record CreateServiceEngineDataSourceRegistrationRequest(
        @Schema(description = "接收数据源连接快照的 DataScalpel 服务引擎 UUID。")
        @NotNull UUID engineId,
        @Schema(description = "要登记的已启用 JDBC 数据源 UUID。DATASCALPEL 要求数据库方言具备 SQL_SERVICE_QUERY 能力；GEOSERVER 仅支持启用 STORAGE 用途且已安装 PostGIS 的 PostgreSQL 数据源。")
        @NotNull UUID dataSourceId
) {
}
