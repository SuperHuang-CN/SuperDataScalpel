package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

@JsonDeserialize(using = CreateDataEntryRequestDeserializer.class)
@Schema(description = "通过已发布填报表单向目标物理表插入一条新记录。")
public record CreateDataEntryRequest(
        @Schema(description = "以当前模型字段编码为键的完整行值；必须恰好包含全部字段，服务端执行类型、空值、码表、关联值和业务主键校验。")
        @NotNull Map<String, Object> values
) {
    public CreateDataEntryRequest {
        values = values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
