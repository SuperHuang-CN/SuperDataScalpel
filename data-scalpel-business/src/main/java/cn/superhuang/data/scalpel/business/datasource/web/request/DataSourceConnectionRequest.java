package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/** Typed input configuration for a concrete connection family. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JdbcDataSourceConnectionRequest.class, name = "JDBC"),
        @JsonSubTypes.Type(value = KafkaDataSourceConnectionRequest.class, name = "KAFKA"),
        @JsonSubTypes.Type(value = S3DataSourceConnectionRequest.class, name = "S3"),
        @JsonSubTypes.Type(value = HttpApiDataSourceConnectionRequest.class, name = "HTTP_API")
})
@Schema(
        description = "数据源连接配置联合类型；kind 必须与外层数据源 type 的 connectionKind 一致",
        discriminatorProperty = "kind"
)
public sealed interface DataSourceConnectionRequest permits JdbcDataSourceConnectionRequest,
        KafkaDataSourceConnectionRequest, S3DataSourceConnectionRequest, HttpApiDataSourceConnectionRequest {

    DataSourceConnectionKind kind();
}
