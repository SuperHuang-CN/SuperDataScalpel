package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.service.HttpApiConfigurationCodec;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

public record HttpApiDataSourceConnectionResponse(
        HttpApiContracts.ConnectionConfiguration configuration
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
