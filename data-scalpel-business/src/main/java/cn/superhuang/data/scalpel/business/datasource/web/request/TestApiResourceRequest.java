package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "使用临时运行参数执行已登记 HTTP API 资源；不保存参数或修改资源，但会真实调用远端接口，ASYNC_JOB 会真实提交并轮询远端任务")
public record TestApiResourceRequest(
        @Schema(description = "替换请求或签名模板中的 ${runtime.<name>} 变量；这里只提交不含 runtime. 前缀的 name。名称不能为空、重复或包含 password、secret、token、credential、API Key、Access Key、signature 等敏感字样")
        @Size(max = 100) List<HttpApiContracts.RuntimeParameter> runtimeParameters
) {
}
