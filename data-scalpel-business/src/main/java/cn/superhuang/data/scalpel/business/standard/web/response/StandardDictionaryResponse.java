package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一张树形业务码表的基础信息和内容版本；节点通过独立树接口读取")
public record StandardDictionaryResponse(
        @Schema(description = "码表 UUID") UUID id,
        @Schema(description = "全局唯一且规范化为大写的码表技术编码；被模型字段或模板字段引用后不能修改") String code,
        @Schema(description = "码表显示名称") String name,
        @Schema(description = "节点 code 的逻辑类型：STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN；被引用后不能修改") PlatformDataType valueType,
        @Schema(description = "码表自身是否启用；停用后保留历史字段绑定，但不能新绑定，所有节点的 effectiveEnabled 均为 false") boolean enabled,
        @Schema(description = "从 1 开始的业务内容版本；基础信息、启停状态或节点树实际变化时每次命令递增一次，无变化命令不递增") int version,
        @Schema(description = "码表业务口径、适用范围或维护说明；未填写时为空") String description,
        @Schema(description = "码表创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "码表基础信息、启停状态或节点内容最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static StandardDictionaryResponse from(StandardDictionary dictionary) {
        return new StandardDictionaryResponse(
                dictionary.getId(),
                dictionary.getCode(),
                dictionary.getName(),
                dictionary.getValueType(),
                dictionary.isEnabled(),
                dictionary.getVersion(),
                dictionary.getDescription(),
                dictionary.getCreatedAt(),
                dictionary.getUpdatedAt()
        );
    }
}
