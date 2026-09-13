package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

@Schema(description = "单条填报记录更新结果。")
public record DataEntryUpdateResponse(
        boolean changed,
        UUID operationLogId,
        String recordKey,
        Map<String, Object> values,
        boolean manualVerificationRequired,
        String warningMessage
) {
    public DataEntryUpdateResponse {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
