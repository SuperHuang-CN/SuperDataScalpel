package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "使用当前模型完整业务主键定位一条填报记录的只读请求。")
public record DataEntryRecordKeyRequest(@NotEmpty Map<String, Object> key) {
    public DataEntryRecordKeyRequest {
        key = key == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(key));
    }
}
