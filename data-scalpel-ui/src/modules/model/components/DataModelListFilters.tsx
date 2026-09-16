import { Form, Select } from 'antd';
import {
  ManagementAdaptiveMoreFilters,
  ManagementFilterActions,
  ManagementSearchInput,
} from '../../../shared/components/ManagementFilters';
import { useDataSources } from '../../datasource';
import { useModelWarehouseLayers } from '../hooks/useDataModels';
import type { DataModelListState } from '../hooks/useDataModelListState';
import {
  dataModelStatusLabels,
  type DataModelFilters,
  type DataModelStatus,
} from '../model/dataModel';

const jdbcDataSourceRequest = {
  page: 0,
  size: 500,
  sort: 'code',
} as const;

export const DataModelListFilters = ({ list }: { list: DataModelListState }) => {
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest);
  const warehouseLayersQuery = useModelWarehouseLayers({
    page: 0,
    size: 500,
    sort: 'sortOrder,code',
  });
  const dataSourceOptions = dataSourcesQuery.data?.content
    .filter((source) => source.connection.kind === 'JDBC')
    .map((source) => ({ value: source.id, label: source.name })) ?? [];
  const warehouseLayerOptions = warehouseLayersQuery.data?.content.map((layer) => ({
    value: layer.id,
    label: `${layer.code} · ${layer.name}${layer.enabled ? '' : '（已停用）'}`,
  })) ?? [];
  const advancedFilterCount = Number(Boolean(list.advancedFilters.storageDataSourceId))
    + Number(Boolean(list.advancedFilters.warehouseLayerId));

  return (
    <div className="management-filter-strip">
      <Form<DataModelFilters>
        autoComplete="off"
        form={list.filterForm}
        layout="inline"
        className="management-filter-form"
        initialValues={list.initialRouteState.filters}
        onFinish={list.applyDirectFilters}
      >
        <Form.Item name="keyword">
          <ManagementSearchInput
            allowClear
            placeholder="搜索模型名称或编码"
            className="data-model-keyword-input"
          />
        </Form.Item>
        <Form.Item name="status">
          <Select
            allowClear
            placeholder="全部状态"
            className="data-model-status-select"
            options={(Object.entries(dataModelStatusLabels) as [DataModelStatus, string][])
              .map(([value, label]) => ({ value, label }))}
          />
        </Form.Item>
      </Form>
      <ManagementAdaptiveMoreFilters
        count={advancedFilterCount}
        open={list.advancedFilterOpen}
        onOpenChange={(open) => {
          list.setAdvancedFilterOpen(open);
          if (open) {
            list.advancedFilterForm.resetFields();
            list.advancedFilterForm.setFieldsValue({
              storageDataSourceId: list.advancedFilters.storageDataSourceId,
              warehouseLayerId: list.advancedFilters.warehouseLayerId,
            });
          }
        }}
        onClear={list.clearAdvancedFilters}
        onCancel={() => {
          list.advancedFilterForm.setFieldsValue({
            storageDataSourceId: list.advancedFilters.storageDataSourceId,
            warehouseLayerId: list.advancedFilters.warehouseLayerId,
          });
          list.setAdvancedFilterOpen(false);
        }}
        onConfirm={list.confirmAdvancedFilters}
      >
        <Form<DataModelFilters>
          form={list.advancedFilterForm}
          layout="vertical"
          autoComplete="off"
          initialValues={list.advancedFilters}
        >
          <Form.Item name="storageDataSourceId" label="JDBC 数据源">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="全部 JDBC 数据源"
              loading={dataSourcesQuery.isFetching}
              options={dataSourceOptions}
              className="advanced-filter-select management-inline-filter-wide"
            />
          </Form.Item>
          <Form.Item name="warehouseLayerId" label="数仓分层">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="全部分层"
              loading={warehouseLayersQuery.isFetching}
              options={warehouseLayerOptions}
              className="advanced-filter-select"
            />
          </Form.Item>
        </Form>
      </ManagementAdaptiveMoreFilters>
      <ManagementFilterActions
        form={list.filterForm}
        appliedFilters={list.filters}
        additionalActive={advancedFilterCount > 0 || list.directorySelection !== undefined}
        loading={list.modelsQuery.isFetching}
        onReset={list.reset}
      />
    </div>
  );
};
