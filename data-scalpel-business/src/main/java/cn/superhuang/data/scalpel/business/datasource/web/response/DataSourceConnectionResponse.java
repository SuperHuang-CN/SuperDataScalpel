package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Browser-safe typed connection projection. Secrets are never returned. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JdbcDataSourceConnectionResponse.class, name = "JDBC"),
        @JsonSubTypes.Type(value = KafkaDataSourceConnectionResponse.class, name = "KAFKA"),
        @JsonSubTypes.Type(value = S3DataSourceConnectionResponse.class, name = "S3")
})
public sealed interface DataSourceConnectionResponse permits JdbcDataSourceConnectionResponse,
        KafkaDataSourceConnectionResponse, S3DataSourceConnectionResponse {

    DataSourceConnectionKind kind();
}
