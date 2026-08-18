package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;

import java.time.Duration;

import java.util.List;

/**
 * Explicit boundary between model orchestration and database-specific physical table operations.
 */
public interface ModelPhysicalTablePort {

    ModelPhysicalTableInspection inspect(DataSource dataSource, DataModel model, List<DataModelField> fields);

    /** Reads fast database-maintained statistics without scanning model data. */
    default TablePhysicalStatistics readStatistics(DataSource dataSource, DataModel model, Duration timeout) {
        return TablePhysicalStatistics.unsupported("当前物理表实现不支持快速统计");
    }

    /** Applies the same model-compatibility rules to metadata that was already read by a caller. */
    ModelPhysicalTableInspection inspect(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            TableMetadata metadata
    );

    /** Reads one external table before its columns are imported into a model definition. */
    default TableMetadata readExternalTable(DataSource dataSource, DataModel model) {
        throw new UnsupportedOperationException("当前物理表实现不支持读取外部表元数据");
    }

    /** Reads a selected table before a model entity exists. */
    default TableMetadata readExternalTable(DataSource dataSource, TableIdentifier table) {
        throw new UnsupportedOperationException("当前物理表实现不支持读取外部表元数据");
    }

    DdlPlan planCreate(DataSource dataSource, DataModel model, List<DataModelField> fields);

    /** Creates a dialect-classified, SQL-free structural change plan for one managed physical table. */
    TableChangePlan planChange(
            DataSource dataSource,
            DataModel model,
            TableDefinition before,
            TableDefinition target
    );

    /** Executes one controlled option from a previously persisted table change plan. */
    void executeChange(
            DataSource dataSource,
            DataModel model,
            TableChangePlan plan,
            TableChangeExecutionMode mode
    );

    ModelPhysicalTableInspection create(DataSource dataSource, DataModel model, List<DataModelField> fields);

    /** Runs one validated standard query against the model's resolved physical table. */
    default StandardQueryResult query(
            DataSource dataSource,
            DataModel model,
            StandardQuery query,
            int maximumRows,
            Duration timeout
    ) {
        throw new UnsupportedOperationException("当前物理表实现不支持标准数据查询");
    }

    default SpatialPreviewMetadata inspectSpatialPreview(
            DataSource dataSource,
            DataModel model,
            List<SpatialPreviewColumn> columns,
            Duration timeout
    ) {
        return SpatialPreviewMetadata.unsupported("当前物理表实现不支持动态空间预览");
    }

    default SpatialPreviewData readSpatialPreview(
            DataSource dataSource,
            DataModel model,
            SpatialPreviewColumn column,
            SpatialPreviewViewport viewport,
            SpatialPreviewLimits limits,
            Duration timeout
    ) {
        throw new UnsupportedOperationException("当前物理表实现不支持动态空间预览");
    }
}
