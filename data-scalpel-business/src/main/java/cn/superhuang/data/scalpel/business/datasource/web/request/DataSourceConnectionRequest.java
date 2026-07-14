package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Input connection configuration. A null password in an update means keep the saved password. */
public record DataSourceConnectionRequest(
        @NotBlank @Size(max = 255) String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotBlank @Size(max = 128) String databaseName,
        @Size(max = 128) String schemaName,
        @NotBlank @Size(max = 128) String username,
        @Size(max = 512) String password,
        @Size(max = 20) Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 512) String> options
) {
}
