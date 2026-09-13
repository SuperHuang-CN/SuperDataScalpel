package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/** Browser-safe typed connection projection. Secrets are never returned. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JdbcDataSourceConnectionResponse.class, name = "JDBC"),
        @JsonSubTypes.Type(value = KafkaDataSourceConnectionResponse.class, name = "KAFKA"),
        @JsonSubTypes.Type(value = S3DataSourceConnectionResponse.class, name = "S3"),
        @JsonSubTypes.Type(value = HttpApiDataSourceConnectionResponse.class, name = "HTTP_API")
})
@Schema(description = "浏览器安全的数据源连接配置；通过 kind 区分具体结构，不返回密码、Token 或密钥。")
public sealed interface DataSourceConnectionResponse permits JdbcDataSourceConnectionResponse,
        KafkaDataSourceConnectionResponse, S3DataSourceConnectionResponse, HttpApiDataSourceConnectionResponse {

    @Schema(description = "连接结构类型，决定其余字段的具体 Schema")
    DataSourceConnectionKind kind();
}
