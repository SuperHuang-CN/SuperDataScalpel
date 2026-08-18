package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;

import java.util.List;

record DataEntryMetadataSnapshot(
        DataEntryForm form,
        DataModel model,
        DataSource dataSource,
        List<DataModelField> fields,
        List<DataEntryModelLookup> lookups
) {
    DataEntryMetadataSnapshot {
        fields = List.copyOf(fields);
        lookups = List.copyOf(lookups);
    }
}
