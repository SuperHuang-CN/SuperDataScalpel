package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = DeleteDataEntryBatchRequestDeserializer.class)
@Schema(description = "按完整业务主键批量删除填报目标表中的既有记录。")
public record DeleteDataEntryBatchRequest(
        @Schema(description = "待删除记录的业务主键映射列表，最多 100 条；每项必须恰好包含模型全部主键字段编码，且不得重复。")
        @NotEmpty @Size(max = 100) List<Map<String, Object>> keys
) {
    public DeleteDataEntryBatchRequest {
        keys = keys == null ? null : keys.stream()
                .map(key -> Collections.unmodifiableMap(new LinkedHashMap<>(key)))
                .toList();
    }
}
