package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "当前数据库方言支持的高级连接参数定义")
public record ConnectionOptionResponse(
        @Schema(description = "写入 JDBC options 的参数键") String key,
        @Schema(description = "供界面展示的中文名称") String label,
        @Schema(description = "参数值类型，决定界面控件和校验方式") String type,
        @Schema(description = "未显式配置时使用的默认值；无默认值时为空") String defaultValue,
        @Schema(description = "枚举型参数允许的值；自由输入型参数为空列表") List<ConnectionOptionChoiceResponse> choices
) {
    static ConnectionOptionResponse from(ConnectionOptionDefinition definition) {
        return new ConnectionOptionResponse(
                definition.key(),
                definition.label(),
                definition.type().name(),
                definition.defaultValue(),
                definition.choices().stream().map(ConnectionOptionChoiceResponse::from).toList()
        );
    }
}
