package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
@Schema(description = "把同一份告警规则覆盖配置批量应用到多个任务或计算引擎。")
public record ApplyAlertOverridesRequest(
        @Schema(description = "目标任务或计算引擎 UUID 列表；对象类型必须与 configuration.ruleType 一致，最多 100 项；重复 UUID 按首次出现顺序去重。")
        @NotEmpty @Size(max=100) List<@NotNull UUID> subjectIds,
        @Schema(description = "应用到每个目标的完整规则配置；configuration.subjectId 的输入值会被忽略，并由 subjectIds 逐项替换。")
        @NotNull @Valid SaveAlertRuleRequest configuration
) {}
