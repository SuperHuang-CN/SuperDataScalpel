package cn.superhuang.data.scalpel.business.dataentry.service;

import java.util.Map;

@FunctionalInterface
public interface DataEntryImportRowSource {

    void read(RowConsumer consumer);

    @FunctionalInterface
    interface RowConsumer {
        void accept(Map<String, Object> row);
    }
}
