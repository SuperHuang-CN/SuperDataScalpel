package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PublishDataServiceRequest(
        @NotBlank @Size(max = 255) String gatewayRoutePath,
        @NotNull DataServiceAccessMode accessMode
) {
}
