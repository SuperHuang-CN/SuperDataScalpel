package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "供模型字段、模板字段绑定选择器和列表展示使用的码表摘要，不包含节点树")
public record StandardDictionarySummaryResponse(
        @Schema(description = "码表 UUID") UUID id,
        @Schema(description = "全局唯一且规范化为大写的码表技术编码") String code,
        @Schema(description = "码表显示名称") String name,
        @Schema(description = "节点 code 的逻辑类型：STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN") PlatformDataType valueType,
        @Schema(description = "码表自身是否启用；停用码表不能建立新字段绑定，但已有绑定继续保留") boolean enabled,
        @Schema(description = "当前业务内容版本；用于识别基础信息、状态或节点树是否已经变化") int version
) {
    public static StandardDictionarySummaryResponse from(StandardDictionary dictionary) {
        if (dictionary == null) {
            return null;
        }
        return new StandardDictionarySummaryResponse(
                dictionary.getId(),
                dictionary.getCode(),
                dictionary.getName(),
                dictionary.getValueType(),
                dictionary.isEnabled(),
                dictionary.getVersion()
        );
    }
}
