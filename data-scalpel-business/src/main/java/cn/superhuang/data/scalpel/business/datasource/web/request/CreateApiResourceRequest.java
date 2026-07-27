package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateApiResourceRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String connectorType,
        Boolean enabled,
        @NotNull @Valid HttpApiContracts.RequestTemplate request,
        @Valid HttpApiContracts.SigningConfiguration signing,
        @NotNull HttpApiContracts.InvocationType invocationType,
        @Valid HttpApiContracts.PaginationConfiguration pagination,
        @Valid HttpApiContracts.AsyncJobConfiguration asyncJob,
        @NotNull @Size(max = 1000) String recordsPointer,
        @NotEmpty @Size(max = 500) List<HttpApiContracts.OutputField> outputFields,
        @NotNull @Valid HttpApiContracts.ExecutionLimits limits
) {
}
