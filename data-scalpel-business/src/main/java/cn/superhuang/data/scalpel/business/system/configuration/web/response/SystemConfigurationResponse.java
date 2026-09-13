package cn.superhuang.data.scalpel.business.system.configuration.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationValueType;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "数据库中可通过管理接口查看和修改的公开系统配置项；当前代码声明为 internal 的初始化标记、部署密钥和外部 YAML 配置不在此响应中。历史公开配置即使已从代码定义中移除也可能继续保留。")
public record SystemConfigurationResponse(
        @Schema(description = "配置记录 UUID，用于提交修改。")
        UUID id,
        @Schema(description = "配置稳定键，创建后不能修改；通常来自代码预置定义，历史记录对应的定义可能已被移除。")
        String configKey,
        @Schema(description = "配置中文名称。")
        String name,
        @Schema(description = "数据库当前保存的字符串表示。INTEGER 已规范化为十进制整数文本，BOOLEAN 已规范化为小写 true 或 false；STRING 可能是普通文本或配置专属的受控内容。启动同步不会用代码默认值覆盖该值，修改后使用方在下一次读取时取得新值。")
        String configValue,
        @Schema(description = "值类型：STRING 文本、INTEGER 十进制整数、BOOLEAN 为 true 或 false。")
        SystemConfigurationValueType valueType,
        @Schema(description = "配置用途及影响范围。")
        String description,
        @Schema(description = "代码预置的建议展示顺序；列表接口不会隐式按该字段排序，需要调用方通过 sort 参数明确指定。")
        Integer sortOrder,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static SystemConfigurationResponse from(SystemConfiguration configuration) {
        return new SystemConfigurationResponse(
                configuration.getId(),
                configuration.getConfigKey(),
                configuration.getName(),
                configuration.getConfigValue(),
                configuration.getValueType(),
                configuration.getDescription(),
                configuration.getSortOrder(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt()
        );
    }
}
