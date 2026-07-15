package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One S3 data source is explicitly scoped to one bucket and an optional root prefix. */
public record S3DataSourceConnectionRequest(
        @NotBlank @Size(max = 255) String endpoint,
        @Size(max = 64) String region,
        @NotBlank @Size(max = 63) String bucket,
        @Size(max = 512) String rootPrefix,
        @NotBlank @Size(max = 128) String accessKey,
        @Size(max = 512) String secretKey,
        Boolean pathStyleAccess
) implements DataSourceConnectionRequest {
    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.S3;
    }
}
