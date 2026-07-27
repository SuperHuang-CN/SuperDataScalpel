package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestApiResourceRequest(
        @Size(max = 100) List<HttpApiContracts.RuntimeParameter> runtimeParameters
) {
}
