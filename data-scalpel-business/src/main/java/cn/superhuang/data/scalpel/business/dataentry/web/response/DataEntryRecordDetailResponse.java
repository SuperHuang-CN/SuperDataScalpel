package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

@Schema(description = "按业务主键重新读取的完整填报记录。")
public record DataEntryRecordDetailResponse(String recordKey, Map<String, Object> values) {
    public DataEntryRecordDetailResponse {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
