package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "把已启用并成功部署的数据服务发布到 API Gateway。")

public record PublishDataServiceRequest(
        @Schema(description = "API Gateway 对外路由路径；去除首尾空白并转小写，必须位于 /open-api/v1/ 下，不得以 / 结尾或包含 //。路径占用由网关确认，冲突返回 409。")
        @NotBlank @Size(max = 255) String gatewayRoutePath,
        @Schema(description = "网关访问模式：PUBLIC 允许匿名调用，SUBSCRIPTION_REQUIRED 只允许持有效订阅凭据的消费者调用。服务仍有任何消费者订阅时不能发布或切换为 PUBLIC。")
        @NotNull DataServiceAccessMode accessMode
) {
}
