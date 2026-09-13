package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonDeserialize(using = UpdateDataEntryRecordRequestDeserializer.class)
@Schema(description = "按完整业务主键更新一条填报记录；values 必须包含当前模型全部字段。")
public record UpdateDataEntryRecordRequest(
        @NotEmpty Map<String, Object> key,
        @NotEmpty Map<String, Object> values
) {
    public UpdateDataEntryRecordRequest {
        key = key == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(key));
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
