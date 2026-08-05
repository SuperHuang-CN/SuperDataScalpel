package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectJdbcQueryRequest(
        @NotBlank @Size(max = 100_000) String sql
) {
}
