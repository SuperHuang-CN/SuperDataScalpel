package cn.superhuang.data.scalpel.business.compute.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "只读目标发现请求；Token 仅用于本次访问，不保存、不注册引擎")
public record DiscoverComputeTargetsRequest(
        @Schema(description = "Dispatcher HTTP/HTTPS 地址，不含凭据、查询串和片段") @NotBlank @Size(max = 500) String dispatcherBaseUrl,
        @Schema(description = "Dispatcher 访问 Token；仅用于鉴权，不在响应中回显") @NotBlank @Size(max = 2000) String accessToken) { }
