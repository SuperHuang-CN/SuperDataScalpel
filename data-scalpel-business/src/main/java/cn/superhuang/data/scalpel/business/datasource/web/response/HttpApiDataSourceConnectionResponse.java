package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.service.HttpApiConfigurationCodec;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "浏览器安全的 HTTP API 连接配置；只返回非敏感字段和凭据已配置状态")
public record HttpApiDataSourceConnectionResponse(
        @Schema(description = "HTTP 基地址、默认请求头、超时、限流、重试和认证公开配置") HttpApiContracts.ConnectionConfiguration configuration
) implements DataSourceConnectionResponse {

    public static HttpApiDataSourceConnectionResponse from(DataSourceConnection connection) {
        return new HttpApiDataSourceConnectionResponse(
                HttpApiConfigurationCodec.readConnectionConfiguration(connection.apiConfigurationValue())
        );
    }

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.HTTP_API;
    }
}
