import { TableOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Empty, Form, Select, Space, Switch, Table, Tooltip } from 'antd';
import { useEffect, type ReactNode } from 'react';
import {
  ManagementFilterActions,
  ManagementSearchInput,
} from '../../../../shared/components/ManagementFilters';
import {
  ManagementCode,
  ManagementDateTime,
  ManagementListCell,
  ManagementStatusIndicator,
} from '../../../../shared/components/ManagementListCells';
import {
  DirectoryTreePanel,
  type DirectorySelection,
  type DirectoryTreeNode,
} from '../../../directory';
import {
  dataModelStatusLabels,
  type DataModelStatus,
  type ModelWarehouseLayer,
} from '../../../model';

export interface ModelSelectionCandidate {
  id: string;
  code: string | null;
  name: string | null;
  status: DataModelStatus | null;
  directoryId: string | null;
  directoryName: string | null;
  warehouseLayerId: string | null;
  warehouseLayerCode: string | null;
  warehouseLayerName: string | null;
  storageDataSourceId: string | null;
  storageDataSourceCode: string | null;
  storageDataSourceName: string | null;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string | null;
  schemaVersion: number | null;
  fieldCount?: number;
  selectable: boolean;
  unavailableReason: string | null;
  updatedAt: string | null;
}

export interface ModelSelectionFilters {
  keyword?: string;
  storageDataSourceId?: string;
  warehouseLayerId?: string;
  status?: DataModelStatus;
}

interface DataSourceFilterOption {
  value: string;
  label: string;
}

interface ModelSelectionWorkspaceProps {
  candidates: ModelSelectionCandidate[];
  total: number;
  loading: boolean;
  error: boolean;
  directories: DirectoryTreeNode[];
  directoriesLoading: boolean;
  directorySelection: DirectorySelection;
  filters: ModelSelectionFilters;
  layers: ModelWarehouseLayer[];
  layersLoading: boolean;
  dataSourceOptions?: DataSourceFilterOption[];
  dataSourcesLoading?: boolean;
  showStatusFilter?: boolean;
  showAvailabilityColumn?: boolean;
  keywordPlaceholder?: string;
  resultTitle: string;
  emptyDescription: string;
  page: number;
  size: number;
  selectionMode: 'radio' | 'checkbox' | 'none';
  selectedRowKeys: string[];
  includeUnavailable?: boolean;
  showUnavailableToggle?: boolean;
  showBrowserControls?: boolean;
  allowRemovingUnavailableSelection?: boolean;
  footer?: ReactNode;
  onDirectorySelectionChange: (selection: DirectorySelection) => void;
  onFiltersChange: (filters: ModelSelectionFilters) => void;
  onReset: () => void;
  onPageChange: (page: number, size: number) => void;
  onCandidateToggle?: (candidate: ModelSelectionCandidate, selected: boolean) => void;
  onCandidatesToggle?: (candidates: ModelSelectionCandidate[], selected: boolean) => void;
  onIncludeUnavailableChange?: (checked: boolean) => void;
  onRetry: () => void;
}

const physicalTableName = (model: ModelSelectionCandidate) => model.physicalTableName ?? '';

export const ModelSelectionWorkspace = ({
  candidates,
  total,
  loading,
  error,
  directories,
  directoriesLoading,
  directorySelection,
  filters,
  layers,
  layersLoading,
  dataSourceOptions,
  dataSourcesLoading = false,
  showStatusFilter = false,
  showAvailabilityColumn = false,
  keywordPlaceholder = '搜索模型名称或编码',
  resultTitle,
  emptyDescription,
  page,
  size,
  selectionMode,
  selectedRowKeys,
  includeUnavailable = false,
  showUnavailableToggle = false,
  showBrowserControls = true,
  allowRemovingUnavailableSelection = false,
  footer,
  onDirectorySelectionChange,
  onFiltersChange,
  onReset,
  onPageChange,
  onCandidateToggle,
  onCandidatesToggle,
  onIncludeUnavailableChange,
  onRetry,
}: ModelSelectionWorkspaceProps) => {
  const [filterForm] = Form.useForm<ModelSelectionFilters>();

  useEffect(() => {
    filterForm.setFieldsValue(filters);
  }, [filterForm, filters]);

  const reset = () => {
    filterForm.resetFields();
    onReset();
  };

  const columns: TableProps<ModelSelectionCandidate>['columns'] = [
    {
      title: '模型', width: 220,
      render: (_, model) => (
        <ManagementListCell
          icon={<TableOutlined />}
          iconLabel={`${model.name ?? model.id}模型`}
          iconTone="slate"
          primary={model.name ?? '模型无法解析'}
          secondary={<ManagementCode value={model.code ?? model.id} />}
        />
      ),
    },
    {
      title: '目录 / 分层', width: 150,
      render: (_, model) => (
        <ManagementListCell
          primary={model.directoryName ?? '未分类'}
          secondary={model.warehouseLayerName ?? '未分层'}
        />
      ),
    },
    {
      title: '状态', width: 100,
      render: (_, model) => model.status ? (
        <ManagementStatusIndicator
          label={dataModelStatusLabels[model.status]}
          tone={model.status === 'PUBLISHED' ? 'success' : model.status === 'DISABLED' ? 'warning' : 'default'}
        />
      ) : <ManagementStatusIndicator label="无法解析" tone="warning" />,
    },
    {
      title: '存储位置', width: 300,
      render: (_, model) => (
        <ManagementListCell
          primary={model.storageDataSourceName ?? '数据源无法解析'}
          secondary={<ManagementCode value={physicalTableName(model) || '—'} title={physicalTableName(model) || undefined} />}
        />
      ),
    },
    {
      title: 'Schema', width: 130,
      render: (_, model) => (
        <ManagementListCell
          primary={model.schemaVersion === null ? '—' : `Schema v${model.schemaVersion}`}
          secondary={model.fieldCount === undefined ? undefined : `${model.fieldCount} 个字段`}
        />
      ),
    },
    {
      title: '更新时间', width: 155,
      render: (_, model) => <ManagementDateTime value={model.updatedAt} />,
    },
  ];
  if (showAvailabilityColumn) columns.push({
    title: '可用状态', width: 170,
    render: (_, model) => model.selectable ? (
      <ManagementStatusIndicator label="可选择" tone="success" />
    ) : (
      <Tooltip title={model.unavailableReason}>
        <span><ManagementStatusIndicator label={model.unavailableReason ?? '不可选择'} tone="warning" /></span>
      </Tooltip>
    ),
  });
  const rowSelection: TableProps<ModelSelectionCandidate>['rowSelection'] = selectionMode === 'none' ? undefined : {
    type: selectionMode,
    preserveSelectedRowKeys: true,
    selectedRowKeys,
    getCheckboxProps: (model) => ({
      disabled: !model.selectable
        && !(allowRemovingUnavailableSelection && selectedRowKeys.includes(model.id)),
    }),
    onSelect: (model, selected) => onCandidateToggle?.(model, selected),
    onSelectAll: (selected, _rows, changedRows) => onCandidatesToggle?.(changedRows, selected),
  };

  return (
    <div className={`model-selection-workspace directory-management-layout${showBrowserControls ? '' : ' model-selection-workspace-flat'}`}>
      {showBrowserControls && (
        <DirectoryTreePanel
          scope="MODEL"
          tree={directories}
          loading={directoriesLoading}
          selection={directorySelection}
          canManage={false}
          showResourceCounts={false}
          onSelectionChange={onDirectorySelectionChange}
        />
      )}
      <section className="management-workbench">
        {showBrowserControls && <div className="management-filter-strip">
          <Form<ModelSelectionFilters>
            form={filterForm}
            layout="inline"
            autoComplete="off"
            className="management-filter-form model-selection-filters"
            onFinish={onFiltersChange}
          >
            <Form.Item name="keyword">
              <ManagementSearchInput allowClear placeholder={keywordPlaceholder} />
            </Form.Item>
            {dataSourceOptions && (
              <Form.Item name="storageDataSourceId">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="全部数据源"
                  loading={dataSourcesLoading}
                  options={dataSourceOptions}
                />
              </Form.Item>
            )}
            {showStatusFilter && (
              <Form.Item name="status">
                <Select
                  allowClear
                  placeholder="全部状态"
                  options={Object.entries(dataModelStatusLabels).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            )}
            <Form.Item name="warehouseLayerId">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="全部数仓分层"
                loading={layersLoading}
                options={layers.map((layer) => ({ value: layer.id, label: `${layer.name}（${layer.code}）` }))}
              />
            </Form.Item>
          </Form>
          <ManagementFilterActions
            form={filterForm}
            appliedFilters={filters}
            additionalActive={directorySelection !== undefined || includeUnavailable}
            loading={loading}
            onReset={reset}
          />
          {showUnavailableToggle && (
            <Space size={6} className="model-selection-unavailable-switch">
              <Switch checked={includeUnavailable} onChange={onIncludeUnavailableChange} />
              <span>显示不可用模型</span>
            </Space>
          )}
        </div>}
        <div className="management-results-surface model-selection-results">
          <div className="management-result-toolbar">
            <div className="management-result-title">
              {resultTitle}
              <span className="management-result-count">共 {total} 项</span>
            </div>
          </div>
          {error && (
            <Alert
              type="error"
              showIcon
              message="模型候选加载失败"
              action={<Button size="small" onClick={onRetry}>重试</Button>}
            />
          )}
          <Table<ModelSelectionCandidate>
            rowKey="id"
            size="small"
            className="management-table model-selection-table"
            loading={loading}
            dataSource={candidates}
            columns={columns}
            scroll={{ x: showAvailabilityColumn ? 1225 : 1055, y: '100%' }}
            locale={{ emptyText: <Empty description={emptyDescription} /> }}
            rowSelection={rowSelection}
            rowClassName={(model) => model.selectable
              ? 'model-selection-row-selectable'
              : 'model-selection-row-unavailable'}
            onRow={selectionMode === 'radio' ? (model) => ({
              onClick: () => {
                if (model.selectable) onCandidateToggle?.(model, true);
              },
            }) : undefined}
            pagination={{
              current: page + 1,
              pageSize: size,
              total,
              size: 'small',
              placement: ['bottomEnd'],
              hideOnSinglePage: false,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 项`,
            }}
            onChange={(pagination) => {
              const nextSize = pagination.pageSize ?? 20;
              onPageChange(nextSize === size ? (pagination.current ?? 1) - 1 : 0, nextSize);
            }}
          />
          {footer}
        </div>
      </section>
    </div>
  );
};
