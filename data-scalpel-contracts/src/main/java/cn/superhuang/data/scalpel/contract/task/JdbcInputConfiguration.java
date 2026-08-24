package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcInputConfiguration(
        String dataSourceId,
        List<JdbcInputTableSelection> tables
) {
    public JdbcInputConfiguration {
        tables = tables == null ? null : List.copyOf(tables);
    }
}
