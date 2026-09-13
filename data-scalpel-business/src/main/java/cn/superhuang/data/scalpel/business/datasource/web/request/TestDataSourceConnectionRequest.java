package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Tests a draft configuration when its runtime client is currently implemented. */
@Schema(description = "测试尚未保存的数据源连接；不创建或修改管理库中的数据源。JDBC 连接数据库，Kafka 读取 Cluster ID，HTTP/空间服务发起远程请求，S3 会写入、列出并删除临时对象")
public record TestDataSourceConnectionRequest(
        @Schema(description = "待测试的具体数据源产品类型")
        @NotNull DataSourceType type,
        @Schema(description = "与 type.connectionKind 匹配的完整连接配置；这是未保存配置，不能沿用已有凭据，测试所需的密码或密钥必须提供")
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
