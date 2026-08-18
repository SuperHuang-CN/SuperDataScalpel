package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.List;

public record DataEntryOptionResponse(
        List<Option> content,
        int pageNo,
        int pageSize,
        boolean hasNext
) {
    public DataEntryOptionResponse {
        content = List.copyOf(content);
    }

    public record Option(Object value, String label, String displayLabel, String status) {
    }
}
