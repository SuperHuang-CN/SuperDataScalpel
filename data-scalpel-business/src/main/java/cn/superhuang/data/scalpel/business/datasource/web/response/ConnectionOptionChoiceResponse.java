package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "高级连接参数的一个可选值")
public record ConnectionOptionChoiceResponse(
        @Schema(description = "提交给后端的稳定值") String value,
        @Schema(description = "供界面展示的中文名称") String label
) {
    static ConnectionOptionChoiceResponse from(ConnectionOptionChoice choice) {
        return new ConnectionOptionChoiceResponse(choice.value(), choice.label());
    }
}
