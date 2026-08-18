package cn.superhuang.data.scalpel.business.dataentry.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DataEntryOptionQueryRequest(
        String keyword,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize,
        @Size(max = 100) List<Object> values
) {
    public DataEntryOptionQueryRequest {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
