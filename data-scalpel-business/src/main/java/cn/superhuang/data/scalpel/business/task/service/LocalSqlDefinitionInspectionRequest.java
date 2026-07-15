package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;

import java.time.Duration;
import java.util.List;

/** Immutable snapshot passed across the management-transaction / external-JDBC boundary. */
public record LocalSqlDefinitionInspectionRequest(
        DataSource dataSource,
        List<ModelWithFields> inputs,
        ModelWithFields output,
        String sql,
        LocalSqlWriteMode writeMode,
        Duration timeout
) {

    public record ModelWithFields(DataModel model, List<DataModelField> fields) {
        public ModelWithFields {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }
}
