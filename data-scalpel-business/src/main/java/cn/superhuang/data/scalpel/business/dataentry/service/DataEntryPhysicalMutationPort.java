package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;

import java.util.List;
import java.util.Map;

public interface DataEntryPhysicalMutationPort {

    DataEntryPhysicalMutationResult insert(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            Map<String, Object> values
    );

    DataEntryPhysicalMutationResult insertBatch(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            DataEntryImportRowSource rows
    );

    DataEntryPhysicalMutationResult insertBatch(
            DataSource dataSource, DataModel model, List<DataModelField> fields,
            DataEntryImportRowSource rows, DataEntryImportBatchListener listener
    );

    Map<String, Object> queryRecord(
            DataSource dataSource, DataModel model, List<DataModelField> fields,
            List<DataModelField> businessKeyFields, Map<String, Object> key
    );

    List<Map<String, Object>> queryRecords(
            DataSource dataSource, DataModel model, List<DataModelField> fields,
            List<DataModelField> businessKeyFields, List<Map<String, Object>> keys
    );

    DataEntryRecordUpdateResult updateRecord(
            DataSource dataSource, DataModel model, List<DataModelField> fields,
            List<DataModelField> businessKeyFields, Map<String, Object> key, Map<String, Object> values
    );

    DataEntryPhysicalMutationResult deleteBatch(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> primaryKeyFields,
            List<Map<String, Object>> keys
    );

    boolean hasDuplicateBusinessKey(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields
    );

    List<DataEntryBusinessKeyMatch> findBusinessKeyMatches(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields,
            List<Map<String, Object>> keys
    );

    List<Map<String, Object>> queryLookupOptions(
            DataSource dataSource,
            DataModel model,
            DataModelField valueField,
            DataModelField labelField,
            String keyword,
            int pageNo,
            int pageSize,
            List<Object> values
    );
}
