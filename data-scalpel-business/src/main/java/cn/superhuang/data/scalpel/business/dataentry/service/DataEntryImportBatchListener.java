package cn.superhuang.data.scalpel.business.dataentry.service;

import java.util.List;
import java.util.Map;

public interface DataEntryImportBatchListener {
    void beforeBatch(List<Map<String, Object>> rows);
    void afterBatch(List<Map<String, Object>> rows, List<Integer> confirmedSuccessIndexes, boolean resultUnknown,
            String errorCode, String errorMessage);
}
